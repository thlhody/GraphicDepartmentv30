module.exports = {
    proxy: "localhost:8447", // Your Spring Boot app (matches server.port=8447)
    files: [
        "src/main/resources/static/**/*",
        "src/main/resources/templates/**/*"
    ],
    watchOptions: {
        ignoreInitial: true,
        ignored: [
            "node_modules",
            "target",
            ".git",
            "*.class",
            "*.jar"
        ]
    },
    reloadDelay: 500, // Wait 500ms before reloading
    reloadDebounce: 1000, // Debounce multiple changes
    open: true, // Auto-open browser
    notify: false, // Disable Browser-Sync notifications
    ui: {
        port: 3001 // Browser-Sync UI port
    },
    port: 3000, // Development server port
    ghostMode: {
        clicks: true,
        forms: true,
        scroll: true
    },
    logLevel: "info",
    logPrefix: "CT3-DEV",
    browser: ["chrome"], // Specify browser (optional)
    cors: true,
    // Middleware to handle Spring Security CSRF
    middleware: function (req, res, next) {
        res.setHeader('X-Forwarded-Proto', 'http');
        next();
    }
};