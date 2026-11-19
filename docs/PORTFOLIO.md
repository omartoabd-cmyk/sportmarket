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

## After deployment

1. Add **Live demo** with the verified HTTPS URL near the top of the root README.
2. Set the same URL under repository **About → Website**.
3. Confirm screenshots render and GitHub Actions passes.
4. On your profile, select **Customize your pins**, select SportMarket, and save.
5. Submit your actual GitHub **profile URL** in the internship field asking for it.

## Review walkthrough

Browse → filter → details/gallery → register → login → create with photo → edit → mark sold → delete. Use a second account to explain backend ownership rejection. Be ready to discuss layered services, Flyway, JWT validation, query pagination, and transaction/file cleanup.

Describe your own contribution and any AI assistance accurately. Avoid claiming production scale or unimplemented features. A live demo supports the GitHub review; it is not a requirement stated by the application question.
