# Simple Frontend Decoupling Example

## Current Situation (Thymeleaf + Spring Boot)

**Your current login.html** (simplified):
```html
<form th:action="@{/login}" method="post">
    <input type="text" name="username" th:field="*{username}">
    <input type="password" name="password" th:field="*{password}">
    <button type="submit">Login</button>
</form>
```

**Your Spring Controller** (stays the same):
```java
@PostMapping("/login")
public String login(@ModelAttribute LoginRequest request) {
    // Your existing login logic
    return "redirect:/dashboard";  // This needs to change to JSON
}
```

## After Decoupling (Static HTML + JavaScript)

### Step 1: Convert to Static HTML
**new-login.html** (no Thymeleaf):
```html
<!DOCTYPE html>
<html>
<head>
    <title>Login - CT3</title>
    <link rel="stylesheet" href="css/login.css">
</head>
<body>
    <form id="loginForm">
        <input type="text" id="username" name="username" required>
        <input type="password" id="password" name="password" required>
        <button type="submit">Login</button>
    </form>

    <script src="js/login.js"></script>
</body>
</html>
```

### Step 2: Add JavaScript to Handle Form
**js/login.js**:
```javascript
document.getElementById('loginForm').addEventListener('submit', async (e) => {
    e.preventDefault();

    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;

    try {
        const response = await fetch('/api/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ username, password })
        });

        if (response.ok) {
            const result = await response.json();
            // Store token if needed
            localStorage.setItem('token', result.token);
            // Redirect to dashboard
            window.location.href = '/dashboard.html';
        } else {
            alert('Login failed');
        }
    } catch (error) {
        console.error('Error:', error);
    }
});
```

### Step 3: Modify Your Controller (minimal change)
```java
@PostMapping("/api/login")  // Changed endpoint to /api/login
@ResponseBody              // Return JSON instead of view
public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
    // Your existing login logic (unchanged)
    boolean success = authService.authenticate(request.getUsername(), request.getPassword());

    if (success) {
        String token = jwtService.generateToken(request.getUsername());
        return ResponseEntity.ok(new LoginResponse(token, "success"));
    } else {
        return ResponseEntity.status(401).body(new LoginResponse(null, "failed"));
    }
}
```

## How to Run Both Versions

### Development Setup:
1. **Keep your Spring Boot running** on `http://localhost:8447`
2. **Serve static files** from a simple HTTP server:
   ```bash
   # In a new folder with your static files
   python -m http.server 3000
   # or
   npx serve . -p 3000
   ```
3. **Access decoupled version** at `http://localhost:3000`
4. **API calls go to** `http://localhost:8447/api/login`

### Key Changes:
- ✅ **Backend API stays same** (just add `/api` prefix and return JSON)
- ✅ **No Spring Security changes needed**
- ✅ **Database logic unchanged**
- ✅ **Static files can be served separately**

### Benefits:
- Frontend and backend can be developed independently
- Frontend can be deployed to CDN
- Multiple frontends (web, mobile) can use same API
- Easier testing of frontend components

This way, you gradually move from server-side rendering to client-side rendering while keeping your existing Spring Boot logic intact!