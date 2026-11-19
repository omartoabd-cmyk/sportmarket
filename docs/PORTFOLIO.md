# GitHub and application checklist

## First repository

Create a public GitHub repository named `sportmarket`. Leave generated README, licence, and gitignore options unchecked when pushing the existing project. Its root must contain both applications, README, and `render.yaml`.

Suggested description:

> Full-stack sports equipment marketplace with React, TypeScript, Spring Boot, PostgreSQL, JWT ownership authorization, search, and validated product photos.

Suggested topics: `java`, `spring-boot`, `react`, `typescript`, `postgresql`, `marketplace`, `jwt`, `full-stack`.

Before pushing, inspect `git status --short` and `git diff --cached --stat`. Credentials, caches, uploads, build output, QA profiles, and private keys should be excluded. GitHub contains code and fictional screenshots, not local PostgreSQL contents.

Once Git is initialized and your Git identity is configured, from the root:

```powershell
git add .
git commit -m "Build SportMarket marketplace"
git branch -M main
git remote add origin https://github.com/YOUR-USERNAME/sportmarket.git
git push -u origin main
```

Replace the username with your actual account. Use GitHub's normal browser/device authentication. Never paste credentials into chat or commit a token.

## Repository presentation

1. Confirm the marketplace, listing details, dashboard, and mobile screenshots render in the README.
2. Check that GitHub Actions passes and local setup instructions are easy to follow.
3. On your profile, select **Customize your pins**, select SportMarket, and save.
4. Submit your actual GitHub **profile URL** in the internship field asking for it.

## Review walkthrough

Browse → filter → details/gallery → register → login → create with photo → edit → mark sold → delete. Use a second account to explain backend ownership rejection. Be ready to discuss layered services, Flyway, JWT validation, query pagination, and transaction/file cleanup.

Describe your own contribution and any AI assistance accurately. Avoid claiming production scale or unimplemented features. Screenshots, source code, tests, and local setup instructions let reviewers assess the project without a hosted demo.
