---
title: Getting Started
layout: default
nav_order: 2
---

# Getting Started

This guide walks you through installing Mini Numbers, completing the setup wizard, creating your first project, and adding the tracking script to your website.

---

## Step 1: Install Mini Numbers

Choose the installation method that works best for you.

### Option A: Docker (recommended)

Docker is the easiest way to get started. If you don't have Docker installed, [get it here](https://docs.docker.com/get-docker/).

```bash
# Download the docker-compose file
curl -O https://raw.githubusercontent.com/OneManStudioDotSe/mini-numbers/main/docker-compose.yml

# Start the server
docker compose up -d
```

The server will be running at `http://localhost:8080`.

### Option B: Pre-built Docker image

```bash
docker run -d -p 8080:8080 --name mini-numbers ghcr.io/onemanstudiodotse/mini-numbers:latest
```

### Option C: Build from source

You'll need [Java 21](https://adoptium.net/) (or newer) installed.

```bash
# Clone the repository
git clone https://github.com/OneManStudioDotSe/mini-numbers.git
cd mini-numbers

# Run the server
./gradlew run
```

### Option D: Build a standalone JAR

```bash
# Build the JAR file
./gradlew buildFatJar

# Run it
java -jar build/libs/mini-numbers-all.jar
```

---

## Step 2: Complete the setup wizard

Once the server is running, open your browser and go to `http://localhost:8080`. You'll be greeted by the setup wizard.

The wizard walks you through five simple steps:

### 1. Security

- **Admin username** — The username you'll use to log in (default: `admin`)
- **Admin password** — Choose a strong password
- **Server salt** — Click "Generate" to create one automatically. This is used internally to protect visitor privacy

### 2. Database

- **SQLite** — The simplest option. Data is stored in a single file. Great for small to medium sites
- **PostgreSQL** — Better for high-traffic sites or when you want a dedicated database server

For most users, SQLite is the right choice.

### 3. Server

- **Port** — The port the server listens on (default: `8080`)
- **Allowed origins** — The domains of websites you want to track (e.g., `https://example.com`). Leave empty for development
- **Development mode** — Turn this on if you're testing locally (it relaxes security rules)

### 4. Privacy

- **Privacy mode** — Choose your privacy level:
  - **Standard** — Full analytics with country and city data
  - **Strict** — Country-level location only (no cities)
  - **Paranoid** — No location or device data at all
- **Hash rotation** — How often visitor identifiers are rotated (default: 24 hours)
- **Data retention** — Automatically delete old data after a set number of days (0 = keep forever)

### 5. Review and save

Review your settings and click **Save**. The application configures itself without needing a restart — you'll be taken straight to the login page.

---

## Step 3: Log in and create a project

1. Log in with the admin credentials you just set up
2. Click **"New project"** in the sidebar
3. Enter your website's **name** and **domain** (e.g., `My Blog` and `blog.example.com`)
4. Click **Create** — you'll get a unique **API key** for this project

Keep this API key handy — you'll need it in the next step.

---

## Step 4: Add the tracking script to your website

Add this single line of code to your website, just before the closing `</head>` tag:

```html
<script async src="https://your-analytics-server.com/tracker/tracker.js" data-project-key="YOUR_API_KEY"></script>
```

Replace:
- `your-analytics-server.com` with your Mini Numbers server URL
- `YOUR_API_KEY` with the API key from Step 3

That's it! Visit your website, then check your Mini Numbers dashboard — you should see your visit appear within seconds.

---

## Step 5: Track custom events (optional)

Want to track specific actions like button clicks or form submissions? Use the JavaScript API:

```javascript
// Track a signup
MiniNumbers.track("signup");

// Track a purchase
MiniNumbers.track("purchase");
```

You can use any event name you like. Events appear in the **Custom Events** section of your dashboard.

---

## What's next?

- **[Dashboard Guide](dashboard-guide)** — Learn what all the charts and numbers mean
- **[Features](features)** — See everything Mini Numbers can do
- **[Configuration](configuration)** — Fine-tune your setup
- **[Deployment](deployment)** — Ready for production? See the deployment guide
