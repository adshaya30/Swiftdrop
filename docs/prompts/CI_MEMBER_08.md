# CI PROMPT: Member #8 (restore and reconcile the pipeline)

Paste the master prompt (v3.0) first in a new AI session, then everything below. This replaces the "no CI/CD" line of MEMBER_08 Prompt 1 and the setup audit prompt.

---

You are **Member #8**. A CI pipeline is **required** (master CR-001, section 12.1). An earlier pipeline existed and the setup prompt removed it. Teams have started working and cannot tell where things conflict. Your job: find out what the old pipeline did, reconcile it with the current repo, and deliver a pipeline with the **nine required checks** that is green on `main` and enforced by branch protection. Do not change application code, tests or thresholds to make it green; if the pipeline exposes a real code failure, report whose it is.

## Step 1: Diagnose (read-only), write `docs/audit/M8-ci.md`

1. Find the old pipeline: `git log --all --diff-filter=D --name-only -- .github` then show the last version (`git show <sha>^:.github/workflows/<file>`). List its jobs, triggers, commands, Java/Node versions, secrets used, and what it checked. If nothing is in history, say so and design it from section 12.1.
2. Inventory the current repo for CI: `.github/workflows/`, `.github/CODEOWNERS`, branch protection (`gh api repos/{owner}/{repo}/branches/main/protection`, ask the leader if you lack permission), recent runs (`gh run list --limit 20`), and the status of every open PR.
3. Run on a clean checkout of `main` and record the result of each: `cd backend && ./mvnw -B -ntp -pl <module> -am verify` for each of the 8 backend module groups (common-core + common-lib as `common`, api-gateway, six services) and in `frontend`: `npm ci`, `npm test -- --coverage`, `npm run build`. For every failure find the **root cause** and classify it: **pipeline** (workflow syntax, wrong working directory, missing `mvnw` executable bit or CRLF, Java/Node version, cache, Docker/Testcontainers), **setup** (parent pom, empty module poms, JaCoCo failing on empty modules, missing scripts, `.gitattributes`), or **member code** (name the member and file by CODEOWNERS).
4. List conflicts between the old pipeline and the new setup (renamed paths, module list, wrapper vs system Maven, JaCoCo thresholds, test commands, branch names, required checks that no longer exist).

## Step 2: Build `.github/workflows/ci.yml` (one file, you own it)

- Triggers: `push` (all branches) and `pull_request` to `main`. **No path filters.** `concurrency` group per ref with cancel-in-progress for non-main.
- **Nine jobs with exactly these names:** `common`, `api-gateway`, `auth-service`, `parcel-service`, `pricing-service`, `delivery-service`, `tracking-service`, `notification-service`, `frontend`. A matrix is fine only if the resulting check names are exactly these nine.
- Backend jobs: `actions/checkout`, `actions/setup-java` (Temurin 21, Maven cache), `cd backend && ./mvnw -B -ntp -pl <module> -am verify` (`common` uses `-pl common-core,common-lib -am`). The JaCoCo gate stays on. Docker is available on `ubuntu-latest` for Testcontainers; do not mock it away. Modules that are still empty skeletons must pass.
- Frontend job: `actions/setup-node` Node 22 with npm cache, `npm ci`, `npm test -- --coverage`, `npm run build`.
- Every job: `actions/upload-artifact` of `**/target/surefire-reports`, `**/target/site/jacoco` (or `frontend/coverage`) with `if: always()`, short retention.
- Least privilege: `permissions: contents: read`. No secrets needed; do not add any. Pin actions by major version at minimum.
- Job timeouts (for example 25 minutes) so a hang does not block everyone.

## Step 3: Fix the setup-side causes you found

Fix every cause classified as **pipeline** or **setup** in files you own (workflow, parent pom, module poms, wrapper, `.gitattributes`, compose, scripts). For **member code** failures, do not touch their files: add them to `docs/audit/M8-ci.md` with member, file, failing check and log link, and include them in your report so the leader can send them back.

## Step 4: Verify on GitHub

1. Push `feature/infra-ci`, watch the run (`gh run watch`), and confirm **all nine checks are green** on that commit. If a check is red only because of member code, say which and proceed; your workflow itself must be correct (green on an untouched skeleton).
2. Prove it blocks: on a throwaway branch (never merged) make one test fail, confirm the matching check turns red and the PR shows it as blocking, then delete the branch.
3. Confirm the artifacts download and the checks show the exact nine names.

## Step 5: Branch protection (give exact settings if you lack permission)

Apply with `gh api` if you may, otherwise give the leader the exact steps: protect `main`; require a pull request; require approval from 1 reviewer (the leader; set CODEOWNERS review for shared files if useful); require the nine status checks by name; require branches to be up to date; no force-push; no deletion; include administrators if the leader wants the rule to bind them too. Update `.github/CODEOWNERS` so `.github/workflows/` is #8's.

## Step 6: Pull request and report

Open the PR (under master 12.2: nine checks green first, **the leader merges**). Final message:
1. Root causes found: pipeline / setup / member code, each with owner and fix status.
2. What the old pipeline did that the new one does or does not, and why.
3. The nine check names and the exact commands each runs (so members can reproduce locally).
4. A short paste-ready team announcement: "Open the Actions tab on your branch; all nine checks must be green before you open a PR; the leader merges; if a check is red, read the job log, fix on your branch, never skip; if the workflow itself looks wrong, message #8."
5. What the leader must still do (branch protection if not applied, any member sent back to fix).

### Done when
`ci.yml` on `main` runs nine correctly named checks, green on `main`, branch protection enforces them, every pipeline or setup conflict is fixed, member-code failures are handed to their owners, and the announcement is ready.
