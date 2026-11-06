# Snyal 2.3 Beta → APK (arm64-v8a, Android 15)

Chaquopy 17.2 + Python 3.13 · AGP 8.13 · Gradle 8.13 · JDK 17
minSdk 26 · compileSdk/targetSdk 35 (Android 15) · ABI: только arm64-v8a

## Что внутри
- **Фоновый сервис** (уведомление «Snyal работает» + кнопка «Остановить») держит Python и веб-дашборд.
- **Окно приложения** показывает дашборд Snyal (WebView → 127.0.0.1:8777).
- `app/src/main/python/snyal.py` — твой Snyal2_3.py **без изменений**. Обновил скрипт — просто замени этот файл.
- `snyal_android.py` — тонкий мост: выставляет HOME, запрашивает root (как в Termux), поднимает дашборд.
- Root — через `su`: при первом запуске появится окно Magisk / KernelSU / APatch. Без root данных меньше (Android 10+).

**Чего в APK нет:** терминального меню и разделов, которые живут только в нём (карта, тепловизор, Джарвис,
чёрный ящик, титры/трейлер, `--screen`), а также Termux:API (`termux-*`). Они остаются в Termux-версии.

## Сборка с телефона (GitHub Actions)
1. Создай на github.com пустой репозиторий (например `snyal-apk`) и токен (Settings → Developer settings → Tokens, право `repo` + `workflow`).
2. В Termux:
   ```
   pkg install git unzip
   unzip Snyal-apk-project.zip && cd snyal-apk
   git init -b main && git add -A && git commit -m "Snyal APK"
   git remote add origin https://github.com/ТВОЙ_НИК/snyal-apk.git
   git push -u origin main
   ```
   (логин — ник, пароль — токен)
3. Репозиторий → вкладка **Actions** → «Build APK» (запускается сам после push, ~5–8 минут).
4. В конце запуска, внизу, **Artifacts** → `Snyal-2.3-arm64-v8a-android15` → скачать zip → внутри `app-release.apk`.

## Сборка на компьютере
Android Studio → Open папку → Build → Build APK(s) в варианте release.
Или: `gradle assembleRelease` (нужны JDK 17, Python 3.13 в PATH и Android SDK 35).
APK: `app/build/outputs/apk/release/app-release.apk`.

## Заметки
- Подпись — отладочным ключом: подходит для личной установки. Для публикации нужен свой keystore.
- Android 15: тип сервиса `specialUse`, разрешение на уведомления, отступы под системные панели (edge-to-edge),
  Python 3.13 в Chaquopy 17 — страницы 16 КБ поддерживаются.
- Дашборд по умолчанию слушает только 127.0.0.1. Чтобы открывать его с другого телефона по Wi-Fi —
  в `snyal_android.py` поставь `LAN = True`.
- Конфиг и журналы лежат в `/data/data/com.snyal.app/files/.snyal/`.
- Сборка Gradle не проверялась в песочнице (нет Android SDK и сети). Python-часть проверена: дашборд поднимается
  из рабочего потока, 403 без токена, 200 с токеном. Если сборка упадёт — пришли лог из Actions.
