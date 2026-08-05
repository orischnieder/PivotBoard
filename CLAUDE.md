# PivotBoard — Android App Build Spec (Claude Code)

You are building **PivotBoard**, a full native Android application, as a graded final
project for a university "Android UI / interface development" course. Build it
incrementally and follow the conventions in §3 EXACTLY — they are copied from the course's
own lesson repos, and the grader expects this precise style. This file is the source of truth.

---

## 1. What the app is

PivotBoard is a **social platform for stock traders** to document and share technical
analysis setups, following the **Episodic Pivot (EP)** methodology. Users post a setup
(ticker + setup type + chart screenshot + notes), follow other traders, like and comment,
keep a personal watchlist, and discover trending tickers.

**Product framing (must appear in-app):** PivotBoard is a tool for documentation and
social learning around technical analysis — **not investment advice**. Show this disclaimer
on the login screen and in an "About" section.

---

## 2. Hard constraints (match the course exactly)

- **Language:** Kotlin only. Idiomatic Kotlin with null-safety. Do NOT introduce Java.
- **SDK / toolchain:** `minSdk 26`, `targetSdk 36`, `compileSdk 36`, `JavaVersion.VERSION_11`,
  `jvmTarget = "11"`, Kotlin `2.0.21`, AGP `8.13.2`. Enable `buildFeatures { viewBinding = true }`.
- **Build:** Gradle **Kotlin DSL** (`build.gradle.kts`) + a **version catalog**
  (`gradle/libs.versions.toml`). Pin every version; no dynamic `+` versions.
- **Backend:** Firebase — **FirebaseUI Auth**, Cloud **Firestore**, **Storage** (Realtime
  Database optional). No custom server. Add the `com.google.gms.google-services` plugin (`4.4.4`).
- **No secrets in git:** never hardcode keys. `google-services.json` (goes in `app/`) and any
  keystore are provided by me and MUST be `.gitignore`d. Where Firebase config is needed, scaffold
  the code and leave `// TODO(ori): add google-services.json` — do NOT invent keys or project IDs.
- Repo must build with `./gradlew assembleDebug` and run with **no unexplained crashes**.

---

## 3. Code conventions — FOLLOW THESE PRECISELY (the grader's house style)

**3.1 Views — ViewBinding**
- `buildFeatures { viewBinding = true }`. Use ViewBinding everywhere — Activities, Fragments,
  ViewHolders. No Kotlin synthetics, no `findViewById` in new code.
    - Activity: `binding = ActivityXxxBinding.inflate(layoutInflater); setContentView(binding.root)`.
    - Fragment: inflate binding in `onCreateView`, return `binding.root`, null it in `onDestroyView`.
    - Adapter: `XxxItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)`.
- In `onCreate`: `enableEdgeToEdge()` then `ViewCompat.setOnApplyWindowInsetsListener(...)` on the
  root, exactly like every course activity.
- **View id naming: `screen_TYPE_description`** in snake_case (ViewBinding → camelCase property).
  TYPE codes: `BTN, IMG, LBL, EDT/ET, RV, FRAME, LAY, FAB, SPN, CRD/CARD, RB, LOTTIE`.
  e.g. `feed_RV_posts` → `binding.feedRVPosts`; `create_EDT_ticker` → `binding.createEDTTicker`.

**3.2 Singleton "Manager" utilities (thread-safe), initialized in a custom `App` class**
- Every manager uses the course's exact thread-safe singleton shape:
  ```kotlin
  class XxxManager private constructor(context: Context) {
      private val contextRef = WeakReference(context)
      companion object {
          @Volatile private var instance: XxxManager? = null
          fun init(context: Context): XxxManager =
              instance ?: synchronized(this) { instance ?: XxxManager(context).also { instance = it } }
          fun getInstance(): XxxManager = instance
              ?: throw IllegalStateException("XxxManager must be initialized by calling init(context) before use.")
      }
  }
  ```
- Register `App : Application()` in the manifest; call `init(this)` on every manager in `onCreate`.
- **Reuse the course utilities (keep names/behavior):** `ImageLoader` (Glide wrapper,
  `loadImage(source, imageView, placeHolder = R.drawable.unavailable_photo)` with
  `.centerCrop().placeholder(...)`), `SignalManager` (`toast`, `vibrate`), `SharedPreferencesManager`,
  `Constants` (an `object` with nested `object`s of `const val`s — incl. `Constants.FIRESTORE`,
  `Constants.SP_KEYS`), `TimeFormatter` (`object`, for relative "2h ago" timestamps), `DataManager`
  (`object`, seed/static data only).
- **New managers for this app, same singleton pattern, using the exact course Firebase idioms:**
    - `AuthManager` — `buildSignInIntent()` (returns the FirebaseUI intent, see §3.5), `currentUid()`,
      `currentUser()`, `isLoggedIn()` (`FirebaseAuth.getInstance().currentUser != null`),
      `logout()` (`AuthUI.getInstance().signOut(context)`).
    - `DatabaseManager` — wraps `Firebase.firestore`. Reads:
      `db.collection(Constants.FIRESTORE.POSTS).get().addOnSuccessListener { for (doc in it) doc.toObject<Post>().apply { id = doc.id } }.addOnCompleteListener { ... }.addOnFailureListener { ... }`.
      Realtime: `.addSnapshotListener { snapshot, e -> snapshot?.documentChanges?.forEach { dc -> when (dc.type) { ADDED, MODIFIED, REMOVED -> ... } } }`
      and return the `ListenerRegistration` so callers can remove it. Writes: `collectionRef.add(obj)`
      (auto-id) or `.document(id).set(obj)` / `.update(map)`; counters via `FieldValue.increment(...)`.
    - `StorageManager` — `Firebase.storage.reference.child("images/<uid>_<uuid>")`,
      `putFile(uri).continueWithTask { ref.downloadUrl }.addOnCompleteListener { task.result }`.
- Async style: **callbacks on Firebase Tasks** (as above) are the course idiom and the default.
  Coroutines with `lifecycleScope.launch { }` are also acceptable (taught in L04) where they make
  sequential async cleaner. Do NOT add Flow/StateFlow or a full MVVM/ViewModel layer — not taught.

**3.3 Models — plain Firestore-friendly data classes (recommended, proven)**
- For any model stored in / read from Firestore (`User`, `Post`, `Comment`, `WatchItem`,
  `AppNotification`), use the SIMPLEST form that `toObject<T>()` supports: a `data class` where
  **every field is `var` with a default value** (no private constructor, no Builder needed):
  ```kotlin
  data class Post(
      var id: String = "",
      var authorId: String = "",
      var ticker: String = "",
      var setupType: String = "",
      var imageUrl: String = "",
      var notes: String = "",
      var createdAt: Long = 0L,
      var likeCount: Long = 0,
      var commentCount: Long = 0
  )
  ```
  After loading, set `model.id = document.id`. This is the pattern proven in a reference project.
  (The course's L09 `MovieItem` uses a private constructor + a no-arg `constructor()` + a Builder;
  that also works, but only add the Builder if you actually want fluent construction — the plain
  form above is cleaner and crash-safe. The one rule you must never break: a Firestore model needs a
  public no-arg path and defaulted fields.)

**3.4 Libraries (exact — via the version catalog)**
- `firebase-bom = 34.7.0` (use the BoM; no versions on individual Firebase libs)
- `firebase-ui-auth = 9.0.0` (`com.firebaseui:firebase-ui-auth`)
- `firebase-firestore`, `firebase-storage`, `firebase-analytics` (and `firebase-database` only if
  you actually use Realtime DB)
- `glide = 5.0.5`, `gson = 2.13.2`, `recyclerview = 1.4.0`, `cardview`, `lottie` (splash)
- `androidx.swiperefreshlayout` (pull-to-refresh), `androidx.viewpager2` (Following/Discover tabs + onboarding)
- Optional: `kotlinx-coroutines-android` + `kotlinx-coroutines-play-services` (lets you `.await()`
  Firebase Tasks inside `lifecycleScope.launch { }` instead of nesting callbacks — a reference project uses this)
- Material 3 (`material 1.13.0`), ConstraintLayout, activity, appcompat, core-ktx (course versions)
- Plugin `google-services = 4.4.4`

**3.5 Auth — FirebaseUI drop-in (the course does NOT hand-roll login screens)**
- A `LoginActivity` launches the FirebaseUI sign-in flow:
  ```kotlin
  val providers = arrayListOf(
      AuthUI.IdpConfig.EmailBuilder().build(),
      AuthUI.IdpConfig.GoogleBuilder().build()
  )
  val intent = AuthUI.getInstance().createSignInIntentBuilder()
      .setLogo(R.drawable.<app_logo>).setAvailableProviders(providers).setTheme(R.style.<AppTheme>).build()
  signInLauncher.launch(intent)   // registerForActivityResult(FirebaseAuthUIActivityResultContract()) { onSignInResult(it) }
  ```
- On `RESULT_OK`: if it's a new user, create/merge `users/{uid}` in Firestore (username, displayName,
  photoUrl), then go to `MainActivity`. Put the "not investment advice" disclaimer on this screen.

**3.6 Navigation — manual, NOT the Jetpack Navigation Component. Two valid styles (pick one):**
- **(A) Fragments + BottomNavigationView (course L06 style):** one host `MainActivity` with a
  `BottomNavigationView` + a `FrameLayout` container; swap with
  `supportFragmentManager.beginTransaction().replace(R.id.main_FRAME_container, fragment).commit()`.
- **(B) Activity-per-screen + optional Navigation Drawer (proven in a reference project):** each major
  screen is its own `Activity` launched via `Intent`; a `MainActivity` hub uses a `BottomNavigationView`
  and/or a `NavigationView` drawer, with a small `NavigationHelper`/`DrawerHelper` utility to wire it.
- Either way: fragments (where used) in a `fragments/` (or `ui/`) package; activities in `activities/`;
  cross-component callbacks as interfaces in `interfaces/` (naming `XxxCallback`, be consistent).
- For the Feed's **Following / Discover** tabs, use `TabLayout` + `ViewPager2` + a small pager adapter.

**3.7 RecyclerView adapter convention (copy the course's `MovieAdapter`)**
- `adapters/XxxAdapter.kt`: `class XxxAdapter(var items: List<Xxx> = listOf()) : RecyclerView.Adapter<XxxAdapter.XxxViewHolder>()`
  with a public `var xxxCallback: XxxCallback? = null`.
- `onCreateViewHolder` inflates `XxxItemBinding`; `inner class XxxViewHolder(val binding) : RecyclerView.ViewHolder(binding.root)`
  wires clicks in `init { }` using `absoluteAdapterPosition`. Provide `getItemCount()`; reassign
  `items` then `notifyDataSetChanged()`/`notifyItemChanged(pos)` on updates.
- Item layout `xxx_item.xml` with a `CardView` root and `xxx_TYPE_desc` ids. Use `LinearLayoutManager`.

---

## 4. Firestore data model

- `users/{uid}` → displayName, username, photoUrl, bio, followersCount, followingCount, postsCount, createdAt
- `posts/{postId}` → authorId, authorName, authorPhotoUrl, ticker (UPPERCASE), setupType, imageUrl, notes, tags[], createdAt, likeCount, commentCount
- `posts/{postId}/comments/{commentId}` → authorId, authorName, text, createdAt
- `posts/{postId}/likes/{uid}` → true (presence = liked; keep `likeCount` on the post doc)
- Follow graph: `users/{uid}/following/{targetUid}` and `users/{targetUid}/followers/{uid}`
- Watchlist: `users/{uid}/watchlist/{ticker}` → { ticker, isPublic, addedAt }
- Notifications: `users/{uid}/notifications/{notifId}` → { type(like|comment|follow), fromUid, fromName, postId?, createdAt, read }

Keep denormalized counters updated on write (`FieldValue.increment`, batched writes). Put all
collection names / field keys in `Constants.FIRESTORE`. Generate a `firestore.rules` file (auth
required; users write only their own data) and note I must publish it in the Firebase console.

---

## 5. Screens & features — build in this order

Build MVP (5.0–5.6) first; confirm each compiles and runs before moving on. Then bonus (§7).

**5.0 Splash** — `SplashScreenActivity` is the launcher (course pattern): play a Lottie animation;
on `onAnimationEnd`, route via `AuthManager.isLoggedIn()` to `LoginActivity` or `MainActivity`, then `finish()`.

**5.1 Auth** — `LoginActivity` with the FirebaseUI drop-in (Email + Google) per §3.5. First login
creates `users/{uid}`. Disclaimer shown here.

**5.2 Feed (home)** — two tabs: **Following** and **Discover**. Post card (RecyclerView + CardView):
author+avatar, ticker chip, setup-type chip, chart image, notes (truncated), like button+count,
comment count, relative timestamp. Pull-to-refresh; empty state for an empty Following feed.

**5.3 Create Post** — ticker (forced uppercase), setup type (spinner, default "Episodic Pivot"),
chart image (Photo Picker: `ActivityResultContracts.PickVisualMedia()` → upload via `StorageManager`),
notes, optional tags. Validate inputs; show upload progress; disable publish while uploading.

**5.4 Post detail** — full post + likes + comments list + add-comment input. A like/comment creates
a notification for the post author.

**5.5 Profile** — avatar, username, bio, counts (posts/followers/following), **Follow/Unfollow**
(own profile shows "Edit profile" instead), and that user's posts.

**5.6 Watchlist** — personal tickers, each toggle **private/public**; tapping a ticker filters
feed/search to it.

**Also:** Search (by ticker or tag) and Notifications (like/comment/new follower, mark-as-read).

---

## 6. UX & design (graded)

- Consistent Material 3 theme; colors/typography in theme files; support **dark mode** (`values-night`).
  Readable **contrast** always.
- Proper spacing and touch targets (≥48dp); **no overlapping elements**; good use of screen space;
  content scrolls and is never cut off.
- Every list screen has explicit **loading, empty, and error** states.
- User-friendly errors via `SignalManager.toast(...)` / Snackbar — never crash on network/permission failure.
- Match the general layout of the project wireframes (feed cards, create-post form, profile header, watchlist rows).

---

## 7. Bonus features (after MVP is stable — the rubric rewards extras)

- **Trending Tickers** — most-posted tickers this week.
- **Advanced search** — filter by ticker, tag, or setup type.
- **Push notifications** via FCM for like/comment/new-follower (or local notifications via a
  `NotificationChannel` + `BroadcastReceiver`, as a reference project does).
- **Statistics screen** with simple charts (e.g. posts per week, most-used setup types, likes received).
- **Onboarding** (first-run `ViewPager2` intro) and a polished **empty state** layout.
- Nice-to-have: edit/delete own post, profile editing, share post, custom confirm/success dialogs,
  expand/collapse long notes with `ObjectAnimator` (course-taught).

Add these only once core is solid. If something is cut or half-done, leave a clear `// TODO` and
list it in the README so I can mention it in the demo video.

---

## 8. Code quality (explicitly graded)

- Proper separation of concerns; clean class division; **no duplicated code** (share logic via
  managers, adapters, extension functions, base classes).
- Meaningful names; small functions; comments only where the *why* isn't obvious.
- Keep Firebase types behind the managers — fragments talk to
  `AuthManager`/`DatabaseManager`/`StorageManager`, not the Firebase SDK directly (the FirebaseUI
  launcher necessarily lives in `LoginActivity`).
- **Remove Firestore listeners** in `onStop`/`onDestroyView` and null Fragment bindings in
  `onDestroyView` to avoid leaks and post-destroy crashes.
- No compiler warnings you can reasonably remove.

---

## 9. Deliverables

- Complete, buildable Android Studio project committed to a **public** git repo.
- Thorough **README.md** in English (note a Hebrew section can be added): overview, feature list,
  screenshots section (placeholders), tech stack, **Firebase setup steps** (create project, add
  `google-services.json`, enable Email + Google providers, add SHA-1 for Google Sign-In, publish
  Firestore rules), build/run instructions, and a **demo-video link** placeholder.
- `.gitignore` covering `google-services.json`, `/build`, `.idea`, keystores, `local.properties`.
- Small, descriptive commits (the git history will be shown).

---

## 10. How to work with me (workflow)

1. First scaffold: Gradle + version catalog, deps + `google-services` plugin, `App` class + manager
   singletons (`Constants`, `SignalManager`, `ImageLoader`, `SharedPreferencesManager`, `TimeFormatter`,
   `AuthManager`, `DatabaseManager`, `StorageManager`), Material theme + `values-night`,
   `SplashScreenActivity` (Lottie), `LoginActivity` (FirebaseUI), `MainActivity` host (BottomNavigationView
    + FrameLayout), empty fragments, and the package skeleton (`ui/ adapters/ interfaces/ model/ utilities/`).
      Confirm it builds **before** feature code.
2. Then implement screens in the §5 order. After each: tell me what you built, how to test it, and any
   Firebase-console step I must do manually.
3. **Pause and ask me** before adding a new third-party dependency, changing the data model, or anything
   needing my Firebase console / SHA-1 / `google-services.json`.
4. Never fabricate credentials, project IDs, or fake data as if real.
5. If unsure between two reasonable approaches, pick the one closest to the course style above and note
   the choice — don't stall.

Confirm you've read this spec, then propose the initial scaffold and the exact dependency list
(with versions from the catalog) before generating it.
