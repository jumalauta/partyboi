# Partyboi

Partyboi is a demoparty management system for smaller parties.
We are nearing the beta phase.

> "The party system and online voting was really simple and worked flawlessly."
>
> - [ggn](https://atariscne.org/news/index.php/68k-inside-from-the-inside-a-party-report-shall-we-say)

Big greetings to these parties for helping with testing:

- 68k Inside
- Winterfärjan
- Reaktor LAN Party

## Requirements

* [Docker](https://www.docker.com/)
* [Docker Compose](https://github.com/docker/compose) (included in Docker Desktop)

## Used technologies

* [Ktor](https://ktor.io/) (HTTP framework)
* [PostreSQL](https://www.postgresql.org/) (database)
* [KotliQuery](https://github.com/seratch/kotliquery) (database client)
* [Flyway Community](https://www.red-gate.com/products/flyway/community/) (database migrations)
* [Kotlin DSL for HTML](https://github.com/Kotlin/kotlinx.html) (templating)
* [Arrow](https://arrow-kt.io/) (functional programming)
* [Scrimage](https://github.com/sksamuel/scrimage) (image processing)
* [QRCode-Kotlin](https://github.com/g0dkar/qrcode-kotlin) (QR code creation)
* [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) (zip file processing)
* [skrape{it}](https://github.com/skrapeit/skrape.it) (HTML scraper for e2e tests)


* [Docker Compose](https://docs.docker.com/compose/) (container orchestration)
* [Docker Hub](https://hub.docker.com/) (image registry)
* [Ansible](https://docs.ansible.com/ansible/latest/index.html) (automation for [partyboi.app](https://partyboi.aoo))
* [GitHub Actions](https://github.com/features/actions) (ci/cd)

## Getting started

1. Clone repository: `git clone https://github.com/ilkkahanninen/partyboi.git && cd partyboi`
2. Copy configuration `cp .env.example .env`
3. Edit `.env` file – especially the passwords and host name
4. Build and run: `docker compose up`

