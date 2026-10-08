# ConnectTag MVI & Base Architecture Library (mvi-lib-kmp)

[![JitPack](https://jitpack.io/v/connecttag/mvi-lib-kmp.svg)](https://jitpack.io/#connecttag/mvi-lib-kmp)

مكتبة المعمارية الأساسية ونمط MVI (Model-View-Intent) للتطبيقات متعددة المنصات (Kotlin Multiplatform):
- **Android** (API 24+)
- **Desktop JVM** (Windows, macOS, Linux)
- **iOS** (Arm64, SimulatorArm64)

---

## 📦 التثبيت والإضافة للمشروع (Installation via JitPack)

### 1. إضافة مستودع JitPack في `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. إضافة التبعية في `build.gradle.kts`:
```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            // For Kotlin Multiplatform:
            implementation("com.github.connecttag.mvi-lib-kmp:mvi-lib-kmp:1.0.0")
        }
    }
}
```

الحزمة الأساسية للمكتبة: `org.connecttag.lib.mvi.*`

---

## 🌟 الميزات الأساسية

1. **`BaseMviViewModel`:**
   - كلاس أساسي متعدد المنصات (KMP) مبني فوق `androidx.lifecycle.ViewModel` الرسمي، يربط دورة الحياة مع محرك `MviStore`.
2. **`BaseStateViewModel`:**
   - كلاس مبسط للشاشات الخفيفة التي لا تتطلب آلة حالات كاملة، مع الحفاظ على نفس عقد الحالة والأحداث `StateFlow` و `Flow<Effect>`.
3. **`MviStore` & `MviWorkflow`:**
   - محرك إدارة حالات ذري مع دعم كامل للـ Reducers، التحقق من صحة الأفعال (Action Validators)، وسياسات منع التكرار والتذبذب (Debounce & Flapping Prevention).
4. **`LoggingMiddleware` & Middlewares:**
   - إمكانية اعتراض وتتبع الأفعال والحالات ومراقبتها في بيئات التطوير.
5. **`PageState` & `asPageStateFlow()`:**
   - كبسولات موحدة لحالات الصفحات (Loading, Success, Error) لتحميل البيانات بسلاسة.
6. **`BaseUseCase` & `ModelResource`:**
   - عقود معمارية نظيفة (Clean Architecture) لطبقة الـ Domain والـ UseCases المشتركة عبر المنصات.
