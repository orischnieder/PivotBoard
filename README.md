# PivotBoard

A native Android app where stock traders document and share technical-analysis setups,
following the **Episodic Pivot (EP)** methodology. Users post a setup (ticker, setup type,
chart screenshot and notes), follow other traders, like and comment, keep a personal
watchlist, and discover trending tickers.

> **PivotBoard is a tool for documentation and social learning around technical analysis.
> Nothing in the app is investment advice.** This disclaimer is shown on the login screen
> and in the About section.

---

## Features

### Implemented

- **Splash** — animated launcher screen that routes to Login or the app shell based on the
  current Firebase session.
- **Authentication** — FirebaseUI drop-in sign-in with **Email/Password** and **Google**.
  The profile document `users/{uid}` is created on first sign-in, and self-heals on later
  launches if it ever goes missing.
- **Feed** — **Following** and **Discover** tabs (TabLayout + ViewPager2), post cards with
  author, ticker, setup type, chart image, truncated notes and relative timestamps.
  Pull-to-refresh, optimistic likes, and explicit loading / empty / error states.
- **Create post** — ticker (forced uppercase, validated), setup-type spinner, chart image via
  the Android Photo Picker, notes and optional tags. Shows upload progress and locks the
  form while publishing.
- **Log out** — with confirmation, from the toolbar overflow menu.
- **Dark mode** — full `values-night` theme.

### Not yet implemented

- Post detail with comments (§5.4)
- Profile with follow/unfollow (§5.5)
- Watchlist (§5.6)
- Search and the notifications screen
- Bonus: trending tickers, advanced search, FCM push, statistics, onboarding

Notification documents *are* already written when a post is liked, so the notifications
screen has data to read once it is built.

---

## Screenshots

_To be added._

| Splash | Login | Feed | Create post |
|---|---|---|---|
| _(screenshot)_ | _(screenshot)_ | _(screenshot)_ | _(screenshot)_ |

---

## Demo video

_Link to be added._

---

## Tech stack

- **Kotlin**, ViewBinding, Material 3
- **minSdk 26 / targetSdk 36 / compileSdk 36**, Java 11 bytecode
- **Gradle Kotlin DSL** with a version catalog (`gradle/libs.versions.toml`)
- Kotlin 2.0.21, AGP 8.13.2, Gradle 8.13
- **Firebase** — FirebaseUI Auth 9.0.0, Cloud Firestore, Cloud Storage, Analytics
  (versions from the Firebase BoM 34.7.0)
- Glide, Gson, Lottie, RecyclerView, CardView, SwipeRefreshLayout, ViewPager2
- Manual navigation (no Jetpack Navigation Component): one host `MainActivity` with a
  `BottomNavigationView` swapping fragments in a `FrameLayout`

---

## Build and run

### Requirements

- Android Studio (recent stable)
- **JDK 17 or newer** — AGP 8.x will not run on an older JDK

### JDK note (important for command-line builds)

This project declares its daemon JVM requirement in `gradle/gradle-daemon-jvm.properties`
(`toolchainVersion=21`), so Gradle will normally **auto-discover** a suitable JDK — including
the one bundled with Android Studio — without any extra configuration.

Builds started **from inside Android Studio** always use the IDE's own Gradle JDK setting
(*Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK*), which
overrides anything in `gradle.properties`. Those should just work.

If you build from the **command line** and your system `JAVA_HOME` points at an older JDK
(Java 8, for example), and Gradle cannot auto-discover a newer one, do **one** of the
following:

1. Point `JAVA_HOME` at a JDK 17+ installation, for example:
   ```bash
   export JAVA_HOME=/path/to/jdk-21          # macOS / Linux
   setx JAVA_HOME "C:\Program Files\Java\jdk-21"   # Windows
   ```
2. Or add this line to `gradle.properties`, pointing at **your own** Android Studio JBR:
   ```properties
   # Windows default
   org.gradle.java.home=C\:\\Program Files\\Android\\Android Studio\\jbr
   # macOS default
   # org.gradle.java.home=/Applications/Android Studio.app/Contents/jbr/Contents/Home
   ```

Deliberately **not** committed: that path is machine-specific and would break the build for
everyone else who clones the repo.

### Steps

```bash
git clone <repository-url>
cd PivotBoard_project
# add app/google-services.json (see Firebase setup below)
./gradlew assembleDebug
./gradlew installDebug     # with a device or emulator connected
```

---

## Firebase setup

The app will **not build** without `app/google-services.json`, and that file is
`.gitignore`d — it is never committed. Create your own Firebase project:

1. **Create a project** at the [Firebase console](https://console.firebase.google.com/).
2. **Register an Android app** with the package name **`com.ori.pivotboard_project`**.
3. **Download `google-services.json`** and place it in the **`app/`** directory (next to
   `app/build.gradle.kts`).
4. **Authentication → Sign-in method** — enable **Email/Password** and **Google**.
5. **Add your debug SHA-1** (Project settings → Your apps → Add fingerprint):
   ```bash
   ./gradlew signingReport
   ```
   Copy the `SHA1` value from the `debug` variant.
   **Then re-download `google-services.json` and replace your copy** — the Google OAuth
   client only appears in the file *after* the fingerprint is registered. Without this,
   Google sign-in fails at runtime with a `DEVELOPER_ERROR`.
   A release/signed build uses a different certificate and needs its own SHA-1 added too.
6. **Create the Firestore database** (start in test mode for development).
7. **Create the Cloud Storage bucket** — required for chart uploads.
8. **Publish the security rules.** Firestore and Storage have **separate** rule sets; both
   must be published, and neither is deployed automatically by the app.
   - Firestore: paste [`firestore.rules`](firestore.rules) into *Firestore Database → Rules*.
   - Storage: paste [`storage.rules`](storage.rules) into *Storage → Rules*.

   Uploads are named `<uid>_<uuid>`, so the Storage rules use that filename prefix to let a
   user replace or delete only their own images while anyone signed in can view them.

Firestore test-mode rules expire after 30 days; once they lapse every read is denied, which
looks like an app bug rather than a rules problem.

---

## Project structure

```
app/src/main/java/com/ori/pivotboard_project/
├── App.kt                  Application class; initializes every manager singleton
├── activities/             SplashScreenActivity, LoginActivity, MainActivity
├── adapters/               PostAdapter, FeedPagerAdapter
├── interfaces/             PostCallback
├── model/                  User, Post, Comment, WatchItem, AppNotification
├── ui/                     Feed, PostList, CreatePost, Watchlist, Notifications, Profile
└── utilities/              Constants, SignalManager, ImageLoader, SharedPreferencesManager,
                            TimeFormatter, DataManager, AuthManager, DatabaseManager,
                            StorageManager
```

Firebase types stay behind `AuthManager` / `DatabaseManager` / `StorageManager`; screens do
not touch the Firebase SDK directly. The one exception is `LoginActivity`, which must own the
FirebaseUI `ActivityResultLauncher`.

### Firestore data model

| Path | Contents |
|---|---|
| `users/{uid}` | displayName, username, photoUrl, bio, followersCount, followingCount, postsCount, createdAt |
| `posts/{postId}` | authorId, authorName, authorPhotoUrl, ticker, setupType, imageUrl, notes, tags[], createdAt, likeCount, commentCount |
| `posts/{postId}/comments/{commentId}` | authorId, authorName, text, createdAt |
| `posts/{postId}/likes/{uid}` | presence means liked |
| `users/{uid}/following/{targetUid}`, `users/{targetUid}/followers/{uid}` | follow graph |
| `users/{uid}/watchlist/{ticker}` | ticker, isPublic, addedAt |
| `users/{uid}/notifications/{notifId}` | type, fromUid, fromName, postId, createdAt, read |

Denormalized counters are kept in step with batched writes and `FieldValue.increment`.

---

## Known gaps

- **No Lottie animation asset.** The splash screen shows the static logo; drop a JSON at
  `res/raw/splash_animation.json` and enable `splash_LOTTIE_animation` to use one.
- **The app logo is a placeholder** vector, not final artwork.
- **User search is not implemented.** Search covers posts by ticker (prefix match) and by
  tag (exact match), but there is no "People" tab for finding traders by name or username.
  Users are reachable by tapping an author anywhere in the app, or through the followers /
  following lists. `UserAdapter` and `UserCallback` already exist, so adding a People tab
  would mean one prefix query on `username` plus a third filter chip.
- **Tag search is an exact match.** Firestore's `arrayContains` cannot do prefixes, so
  `earn` will not find `earnings`. Ticker search *is* a prefix match.
- Reading which posts the current user has liked costs one read per post; fine at the
  current page size, but worth revisiting if the feed ever paginates deeply.
