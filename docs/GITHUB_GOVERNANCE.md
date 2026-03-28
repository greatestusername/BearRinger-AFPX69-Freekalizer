# GitHub governance (branch protection and reviews)

`CODEOWNERS` and Actions live in the repo; **branch protection rules** are configured in the GitHub UI (or API) and cannot be checked in as files.

## CODEOWNERS

The file [`.github/CODEOWNERS`](../.github/CODEOWNERS) requests review from `@greatestusername` on all changes. For this to matter, enable **Require review from Code Owners** on the protected branch (below).

If your GitHub username is not `greatestusername`, edit that file to your `@handle` or add a team: `* @myorg/myteam`.

## Branch protection for `master`

In the repository on GitHub: **Settings → Branches → Add branch protection rule**.

Suggested settings:

1. **Branch name pattern:** `master`
2. **Require a pull request before merging** — on  
   - **Required approvals:** at least **1**  
   - **Dismiss stale pull request approvals when new commits are pushed** — optional but recommended  
   - **Require review from Code Owners** — on (pairs with `CODEOWNERS`)
3. **Require status checks to pass before merging** — on  
   - Add the status check that matches this workflow (often **`build-and-test`** under workflow **PR checks**), defined in [`.github/workflows/pr.yml`](../.github/workflows/pr.yml)
4. **Require conversation resolution before merging** — optional
5. **Do not allow bypassing the above settings** — recommended for admins if you want rules to apply to everyone

After the first PR workflow run, the exact status check name appears in the branch protection “search for status checks” box.

## CI and manual APK builds

- Pull requests: workflow **`PR checks`** (`pr.yml`) runs tests and `:app:assembleDebug`.
- Manual APK: workflow **`Build and release APK`** (`release-apk.yml`) — **Actions** tab → select workflow → **Run workflow**. It uploads an artifact; if you enter a **tag**, it also creates a **GitHub Release** with the APK attached.

Signed **release** builds are not configured in Gradle yet; the manual workflow builds **debug** APKs suitable for sideloading. Add keystore secrets and `signingConfigs` when you want store-ready artifacts.
