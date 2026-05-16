# MintyNex Backend

Spring Boot + PostgreSQL (Supabase) — Phase 1 Foundation

---

## Project Structure

```
src/main/java/com/mintynex/
├── MintyNexApplication.java
├── config/
│   └── SecurityConfig.java          ← Spring Security + CORS + JWT
├── security/
│   ├── JwtUtils.java                ← Token generation & validation
│   ├── JwtAuthFilter.java           ← Per-request JWT extraction
│   └── UserDetailsServiceImpl.java  ← Loads User from DB
├── exception/
│   ├── GlobalExceptionHandler.java  ← Consistent JSON error responses
│   ├── BadRequestException.java
│   ├── NotFoundException.java
│   └── ConflictException.java
├── auth/
│   ├── model/   RefreshToken, OtpCode
│   ├── repository/
│   ├── dto/     AuthDto (all request/response DTOs)
│   ├── service/ AuthService, OtpService
│   └── controller/ AuthController
├── users/
│   ├── model/   User (implements UserDetails)
│   ├── repository/
│   └── controller/ UserController
├── posts/
│   ├── model/   Post, Comment, PostLike
│   ├── repository/
│   └── controller/ PostController
├── binder/
│   ├── model/   BinderCard
│   ├── repository/
│   └── controller/ BinderController
├── messages/
│   ├── model/   Message
│   ├── repository/
│   └── controller/ MessageController
└── notifications/
    ├── model/   Notification
    ├── repository/
    └── controller/ NotificationController
```

---

## How to Connect to Supabase

1. Go to **Supabase Dashboard → Your Project → Settings → Database**
2. Under **Connection string**, select **JDBC** tab
3. Copy the URI — it looks like:
   ```
   jdbc:postgresql://db.XXXXXXXXXXXX.supabase.co:5432/postgres
   ```
4. Open `src/main/resources/application.properties` and paste it:
   ```properties
   spring.datasource.url=jdbc:postgresql://db.XXXXXXXXXXXX.supabase.co:5432/postgres
   spring.datasource.username=postgres
   spring.datasource.password=YOUR_SUPABASE_DB_PASSWORD
   ```
5. The password is the one you set when creating the Supabase project.

> **Note:** `spring.jpa.hibernate.ddl-auto=update` will auto-create tables on first run.
> Switch to `validate` after your schema is stable.

---

## Running Locally

```bash
# Requires Java 17 and Maven
./mvnw spring-boot:run
```

Server starts at: `http://localhost:8080`

---

## Adding OTP / SMS (Twilio)

1. Sign up at [twilio.com](https://twilio.com) and get a free trial number.
2. Add to `pom.xml`:
   ```xml
   <dependency>
       <groupId>com.twilio.sdk</groupId>
       <artifactId>twilio</artifactId>
       <version>9.14.0</version>
   </dependency>
   ```
3. Add to `application.properties`:
   ```properties
   twilio.account-sid=ACxxxxxxxxxxxxxxxxxxxx
   twilio.auth-token=your_auth_token
   twilio.from-number=+1XXXXXXXXXX
   ```
4. In `OtpService.java`, replace the `deliverSms()` stub with:
   ```java
   Twilio.init(accountSid, authToken);
   Message.creator(
       new PhoneNumber(phone),
       new PhoneNumber(fromNumber),
       messageText
   ).create();
   ```

---

## Deploying to Railway

1. Push code to GitHub.
2. Go to [railway.app](https://railway.app) → New Project → Deploy from GitHub.
3. Add a **PostgreSQL** plugin (or use Supabase — either works).
4. Set these environment variables in Railway:
   ```
   SPRING_DATASOURCE_URL=jdbc:postgresql://...
   SPRING_DATASOURCE_USERNAME=postgres
   SPRING_DATASOURCE_PASSWORD=...
   JWT_SECRET=your_long_random_secret
   JWT_ACCESS_TOKEN_EXPIRY_MS=3600000
   JWT_REFRESH_TOKEN_EXPIRY_MS=2592000000
   CORS_ALLOWED_ORIGINS=https://mintynex.netlify.app
   ```
5. Railway auto-builds Spring Boot with Maven.

---

## API Endpoints Quick Reference

| Method | Endpoint                        | Auth?  | Description                      |
|--------|---------------------------------|--------|----------------------------------|
| POST   | /api/auth/register              | No     | Register + send phone OTP        |
| POST   | /api/auth/login                 | No     | Login with username + password   |
| POST   | /api/auth/send-otp              | No     | Send OTP SMS                     |
| POST   | /api/auth/verify-otp            | No     | Verify OTP → get JWT tokens      |
| POST   | /api/auth/refresh               | No     | Refresh access token             |
| POST   | /api/auth/reset-password        | No     | Reset password via OTP           |
| POST   | /api/auth/logout                | Yes    | Logout (revoke refresh token)    |
| GET    | /api/users/me                   | Yes    | Get own profile                  |
| PUT    | /api/users/me                   | Yes    | Update profile                   |
| GET    | /api/users/{id}                 | Yes    | Get any user profile             |
| GET    | /api/posts                      | No     | Public feed                      |
| POST   | /api/posts                      | Yes    | Create post                      |
| DELETE | /api/posts/{id}                 | Yes    | Delete own post                  |
| POST   | /api/posts/{id}/like            | Yes    | Like a post                      |
| GET    | /api/posts/{id}/comments        | Yes    | Get comments                     |
| POST   | /api/posts/{id}/comments        | Yes    | Add comment                      |
| GET    | /api/binder                     | Yes    | Get own card binder              |
| POST   | /api/binder                     | Yes    | Add card to binder               |
| DELETE | /api/binder/{id}                | Yes    | Remove card                      |
| GET    | /api/messages/{userId}          | Yes    | Get conversation                 |
| POST   | /api/messages/{userId}          | Yes    | Send message                     |
| PUT    | /api/messages/{userId}/read     | Yes    | Mark messages as read            |
| GET    | /api/notifications              | Yes    | Get notifications                |
| GET    | /api/notifications/unread-count | Yes    | Unread count                     |
| PUT    | /api/notifications/read-all     | Yes    | Mark all as read                 |
