# Releasing

The [Release workflow](https://github.com/SonarSource/sonar-scanner-java-library/actions/workflows/release.yml)
uses the [v7 draft-first release flow](https://github.com/SonarSource/gh-action_release/blob/v7/README.md#migrating-from-v6-to-v7-draft-first-workflow_dispatch).

1. Select the branch or commit corresponding to the successfully built and promoted version.
2. Go to **Actions → Release → Run workflow**, select that ref, and enter the full version including its build number (for example, `1.2.3.456`).
   Alternatively, from this repository, run:

   ```sh
   gh workflow run release.yml --ref <release-ref> -f version=<full-version>
   ```

The workflow creates or reuses a draft GitHub release, runs releasability checks, promotes the artifacts,
publishes to binaries and Maven Central, and publishes the GitHub release after those steps succeed.
Publishing a release manually in the GitHub UI no longer starts the release workflow.

If the workflow fails, fix the cause and re-run the failed workflow from GitHub Actions with the same version.
The draft is retained for retries; a rebuild is not required. An already published release cannot be reused.
