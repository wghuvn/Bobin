# Repository workflow

- Use the existing checkout. Cloud tasks are already isolated; do not create a Git worktree unless the user requests one.
- After completing repository changes, run the relevant checks, commit the task's changes on `main`, and push to `origin/main`.
- The user has given standing authorization for this commit-and-push workflow. Do not ask for confirmation again for routine commits and pushes to `main`.
- Fetch `origin/main` before publishing and preserve local and remote work. Use a normal push; do not force-push without separate explicit authorization.
- Preserve pre-existing user changes and do not include unrelated changes or secrets in a commit.
