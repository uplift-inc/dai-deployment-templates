package ai.h2o.mojos.deploy.aws.lambda;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class InitializationLogger implements LambdaLogger {
  private static final Logger logger = LoggerFactory.getLogger(InitializationLogger.class);

  public void log(String message) {
    logger.info(message);
  }

  public void log(byte[] message) {
    logger.info(new String(message));
  }
}
