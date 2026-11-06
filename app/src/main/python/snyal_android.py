"""Мост между Android-приложением и Snyal.

SnyalService.java вызывает start() из рабочего потока: поднимается веб-дашборд Snyal,
а окно приложения открывает его в WebView. Терминального меню в APK нет —
только дашборд (+ Telegram-бот, если в конфиге задан tg_token).
Сам скрипт (snyal.py) не изменён — это копия Snyal2_3.py.
"""
import os
import threading

# По умолчанию дашборд слушает только сам телефон (127.0.0.1).
# Чтобы открывать его с другого телефона по Wi-Fi, как `--web` в Termux, поставь True.
LAN = False

_lock = threading.Lock()
_state = {"url": None, "mod": None}


def start(files_dir, port=8777):
    with _lock:
        if _state["url"]:
            return _state["url"]

        os.environ["HOME"] = files_dir          # конфиг и журналы Snyal: <files>/.snyal
        os.environ.setdefault("TERM", "dumb")

        import signal
        _orig_signal = signal.signal

        def _signal(*a, **k):                   # signal.signal() работает только в главном потоке
            try:
                return _orig_signal(*a, **k)
            except ValueError:
                return None
        signal.signal = _signal

        import snyal as S                       # HOME должен быть выставлен ДО импорта

        if not LAN:
            _httpd = S.ThreadingHTTPServer

            def _local_only(addr, handler):
                return _httpd(("127.0.0.1", addr[1]), handler)
            S.ThreadingHTTPServer = _local_only

        S.NO_INTRO[0] = True
        S.load_cfg()
        S.load_props()
        S.detect_lang()
        S.apply_theme(S.CFG.get("theme", "red"))
        S.init_root()                           # root как в Termux: окно Magisk / KernelSU / APatch
        S.CFG["web_port"] = int(port)
        web = S.start_web()
        if S.CFG.get("tg_token"):
            S.safe(S.start_bot)

        _state["mod"] = S
        _state["url"] = "http://127.0.0.1:%d/?k=%s" % (web.port, web.token)
        return _state["url"]


def stop():
    S = _state["mod"]
    if S is None:
        return
    for name in ("WEB", "BOT", "SCR"):
        obj = getattr(S, name, None)
        if obj is not None:
            S.safe(obj.stop)
    S.safe(S.save_cfg)
    _state["url"] = None
