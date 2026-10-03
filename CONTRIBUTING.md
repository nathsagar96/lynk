## Branch Protection

### Protected Branches

The following branches are protected and cannot be modified directly:

- `main` - Primary production branch
- `develop` - Development branch for feature integration

### How to Modify Protected Branches

**Do NOT push directly to protected branches.** Instead:

1. Create a feature branch from `develop`
2. Make your changes in the feature branch
3. Create a pull request targeting `develop` (for features) or `main` (for hotfixes)
4. Ensure the pull request passes all CI checks (tests, linting, etc.)
5. Get approval from a maintainer (if required)
6. Merge through the pull request (using squash or rebase)

### Required Approvals

Protected branch merges typically require:
- At least one maintainer approval
- All required status checks to pass
- Successful CI/CD pipeline run

### Branch Naming Convention

Feature branches should follow the pattern:
- `feature/<user>/<feature-name>`
- `fix/<user>/<issue-number>`

### Emergency Fixes

If a hotfix is needed directly on `main`:
1. Contact a maintainer immediately
2. The maintainer will create a hotfix branch
3. Follow the standard PR process for merging to `main`