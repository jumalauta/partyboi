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

## Getting started

1. Clone repository: `git clone https://github.com/ilkkahanninen/partyboi.git && cd partyboi`
2. Copy configuration `cp .env.example .env`
3. Edit `.env` file – especially the passwords and host name
4. Build and run: `docker compose up`

