package ai.h2o.mojos.deploy.aws.lambda;

import ai.h2o.mojos.deploy.common.rest.model.ScoreRequest;
import ai.h2o.mojos.deploy.common.rest.model.ScoreResponse;
import ai.h2o.mojos.deploy.common.transform.MojoFrameToResponseConverter;
import ai.h2o.mojos.deploy.common.transform.RequestChecker;
import ai.h2o.mojos.deploy.common.transform.RequestToMojoFrameConverter;
import ai.h2o.mojos.deploy.common.transform.SampleRequestBuilder;
import ai.h2o.mojos.deploy.common.transform.ScoreRequestFormatException;
import ai.h2o.mojos.runtime.MojoPipeline;
import ai.h2o.mojos.runtime.frame.MojoFrame;
import ai.h2o.mojos.runtime.lic.LicenseException;
import ai.h2o.mojos.runtime.readers.MojoPipelineReaderBackendFactory;
import ai.h2o.mojos.runtime.readers.MojoReaderBackend;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/*
 * AWS lambda request handler that implements scoring using a H2O DAI mojo.
 *
 * <p>The scorer code is shared for all mojo deployments but is repackaged with each deployment.
 */
public final class MojoScorer {
  private static final Object pipelineLock = new Object();
  private static MojoPipeline pipeline;

  private final RequestToMojoFrameConverter requestConverter = new RequestToMojoFrameConverter();
  private final MojoFrameToResponseConverter responseConverter = new MojoFrameToResponseConverter();
  private final RequestChecker requestChecker = new RequestChecker(new SampleRequestBuilder());

  /** Processes a single {@link ScoreRequest} in the given AWS Lambda {@link Context}. */
  public ScoreResponse score(ScoreRequest request, Context context)
      throws IOException, LicenseException, ScoreRequestFormatException {
    LambdaLogger logger = context.getLogger();
    logger.log("Got scoring request");
    MojoPipeline mojoPipeline = getMojoPipeline(logger);
    requestChecker.verify(request, mojoPipeline.getInputMeta());
    logger.log("Scoring request verified");
    MojoFrame requestFrame = requestConverter.apply(request, mojoPipeline.getInputFrameBuilder());
    logger.log(
        String.format(
            "Input has %d rows, %d columns: %s",
            requestFrame.getNrows(),
            requestFrame.getNcols(),
            Arrays.toString(requestFrame.getColumnNames())));
    MojoFrame responseFrame = mojoPipeline.transform(requestFrame);
    logger.log(
        String.format(
            "Response has %d rows, %d columns: %s",
            responseFrame.getNrows(),
            responseFrame.getNcols(),
            Arrays.toString(responseFrame.getColumnNames())));

    ScoreResponse response = responseConverter.apply(responseFrame, request);
    response.id(mojoPipeline.getUuid());
    return response;
  }

  private static MojoPipeline getMojoPipeline(LambdaLogger logger)
      throws IOException, LicenseException {
    synchronized (pipelineLock) {
      if (pipeline == null) {
        pipeline = loadMojoPipelineFromLocalFile(logger);
      }
      return pipeline;
    }
  }

  private static MojoPipeline loadMojoPipelineFromLocalFile(LambdaLogger logger)
      throws IOException, LicenseException {
    logger.log("Loading Mojo from Lambda Deployment Zip");
    try (MojoReaderBackend mojoReaderBackend =
        MojoPipelineReaderBackendFactory.createReaderBackend("pipeline.mojo")) {
      MojoPipeline mojoPipeline = MojoPipeline.loadFrom(mojoReaderBackend);
      logger.log(String.format("Mojo pipeline successfully loaded (%s).", mojoPipeline.getUuid()));
      return mojoPipeline;
    }
  }
}
