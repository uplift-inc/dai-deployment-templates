# DAI Deployment Template for AWS Lambda

This package contains:
* Sources of the generic lambda implementation in: `lambda-template`
* Parameterized terraform files for pushing the lambda to AWS in:
  `terraform-recipe`


## Building the `lambda-template`

The code of the AWS Lambda scorer is a gradle project build as usual by
`./gradlew build`. The build result is a Zip archive
`lambda-template/build/distributions/lambda-template.zip` containing a general
Mojo scorer that can be directly pushed to AWS.

Unlike the H2O Scorer this was forked from, this scorer packages the Mojo with the 
Lambda Wrapper Code.  This is better aligned with our CICD pipeline at Uplift.  To 
achieve that, the **build is packaged expecting a file named `pipeline.mojo` to be 
present in the root directory of `lambda-template.zip`**.  While the filename could 
have been made configurable, it was easy enough to manipulate artifacts in our build 
pipeline to avoid any confusion due to paths.  The `pipeline.mojo` file must be 
injected into the zip archive after the fact.

The Scorer relies on the following environment variable:
* `DRIVERLESS_AI_LICENSE_KEY`: The Driverless license key.

The scorer will also inject the following environment variables, if present:
* `MOJO_FINGERPRINT`: The (currently unused) MD5 Sum of the `pipeline.mojo`
* `TAG`: External tracking tag, such as a version control ID

The Scorer will inject these values into the score response with the `fingerprint` 
and `tag` JSON fields (respectively) so that the exact version from Version Control 
that was used for each scoring event can be tracked.  If the values are not present, 
the values "TEST" and "LOCAL" (respectively) will be used for those fields.

## Pushing to AWS Using Terraform

This deployment template is meant to be used by Driverless AI backend directly,
not by hand. The following describes step necessary to push the lambda by hand,
e.g., for testing purposes.

### One-off Setup

Install terraform `0.11.10` following steps in:
https://www.terraform.io/downloads.html.

Initialize terraform by running `terraform init` in the `terraform-recipe`
folder.
This will download all necessary Terraform plugins, e.g., the AWS one.

### Pushing Lambda to AWS

The Terraform recipe in `terraform-recipe` relies on a few variables you need
to provide either by hand, from command line, or by setting corresponding
environmental variables (using the prefix `TF_VAR_`):
* `access_key`: The access key to your AWS account.
* `secret_key`: The secret key to your AWS account.
* `region`: AWS region to push to (optional, defaults to `us-east-1`).
* `lambda_id`: Id of the resulting AWS lambda. Keep that unique as it is also
  used to store other fragments, e.g., the Mojo file in S3.
* `lambda_zip_path`: Local path to the actual lambda scorer distribution, see
  above (optional, defaults to the relative path to the build Zip archive
  above).
* `mojo_path`: Local path to the mojo file to be pushed to S3. You may get one,
  e.g., by running Driverless AI on `test/data/iris.csv` and asking it to
  create and download the Mojo scoring pipeline in the UI.
* `license_key`: Driverless AI license key.
* `bucket_name`: Name of AWS S3 bucket to store the mojo to so that the lambda
  can access and load it. Note that the bucket has to already exist and
  the effective AWS account has to have write access to it.

Once all the non-optional variables are set, the following command will push
the lambda (or update any changes thereof): `terraform apply`.

Upon successful push, Terraform will output the URL of the lambda endpoint and
the corresponding `api_key`.
Note that the recipe sets up AWS API Gateway proxy, see `api_gateway.tf`.
Look for `base_url` and `api_key` in the output.

```text
Outputs:

api_key = DXQtiCbqEY6xjXWP1MMCu4nkDTwRgfdX2qZoKm3e
base_url = https://mslmi91tni.execute-api.us-east-1.amazonaws.com/test
```

To test the endpoint, send a request to this URL appended by `score` and include
the `api_key` in the request header `x-api-key`, e.g., as follows.

```bash
$ curl \
    -X POST \
    -H "x-api-key: DXQtiCbqEY6xjXWP1MMCu4nkDTwRgfdX2qZoKm3e" \
    -d @test.json https://mslmi91tni.execute-api.us-east-1.amazonaws.com/test/score
```

This expects a file `test.json` with the actual scoring request payload.
If you are using the mojo trained in `test/data/iris.csv` as suggested above,
you should be able to use the following json payload:

```json
{
  "fields": [
    "sepal_len", "sepal_wid", "petal_len", "petal_wid"
  ],
  "includeFieldsInOutput": [
    "sepal_len"
  ],
  "rows": [
    [
      "1.0", "1.0", "2.2", "3.5"
    ],
    [
      "3.0", "10.0", "2.2", "3.5"
    ],
    [
      "4.0", "100.0", "2.2", "3.5"
    ]
  ]
}
```

The expected response should follow this structure, but the actual values may differ:

```json
{
  "id": "a12e7390-b8ac-406a-ade9-0d5ea4b63ea9",
  "fields": [
    "sepal_len",
    "class.Iris-setosa",
    "class.Iris-versicolor",
    "class.Iris-virginica"
  ],
  "score": [
    [
      "1.0",
      "0.6240277982943945",
      "0.045458571508101536",
      "0.330513630197504"
    ],
    [
      "3.0",
      "0.7209441819603676",
      "0.06299909138586585",
      "0.21605672665376663"
    ],
    [
      "4.0",
      "0.7209441819603676",
      "0.06299909138586585",
      "0.21605672665376663"
    ]
  ]
}
```

Note that including the `fields` in the response can be disabled by setting `noFieldNamesInOutput`
to true in the input request.
