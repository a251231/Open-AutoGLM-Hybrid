"""
phone_agent CLI：轮询 Helper，拉取待办命令并通过 PhoneController 执行
"""

import argparse
import logging
import os
import sys
import time

# 将 ~/.autoglm（部署脚本放置的位置）加入搜索路径，便于独立运行
HOME_DIR = os.path.expanduser("~")
DEFAULT_EXTRA_PATH = os.path.join(HOME_DIR, ".autoglm")
if DEFAULT_EXTRA_PATH not in sys.path:
    sys.path.insert(0, DEFAULT_EXTRA_PATH)

from phone_controller import PhoneController, HELPER_URL, load_config_from_app  # noqa: E402


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Open-AutoGLM Helper 轮询执行器")
    parser.add_argument(
        "--helper-url",
        default=os.environ.get("AUTOGLM_HELPER_URL", HELPER_URL),
        help="Helper 服务地址，默认读取环境变量 AUTOGLM_HELPER_URL",
    )
    parser.add_argument(
        "--interval",
        type=int,
        default=int(os.environ.get("AUTOGLM_POLL_INTERVAL", 2)),
        help="轮询间隔（秒），默认 2",
    )
    parser.add_argument(
        "--max-idle",
        type=int,
        default=int(os.environ.get("AUTOGLM_MAX_IDLE", 0)),
        help="允许连续空闲次数，0 表示无限循环",
    )
    parser.add_argument(
        "--no-clear",
        action="store_true",
        help="获取指令后不清空队列（用于调试）",
    )
    parser.add_argument(
        "--once",
        action="store_true",
        help="仅拉取并尝试执行一次待办指令",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s - %(levelname)s - %(message)s",
    )

    # 尝试从 App 拉取 LLM 配置（需 Helper 已运行且 Token 正确）
    load_config_from_app(helper_url=args.helper_url)

    controller = PhoneController(helper_url=args.helper_url)
    try:
        if args.once:
            ok, content = controller.process_pending_command(clear=not args.no_clear)
            if content is None:
                logging.info("未获取到待执行指令")
                return 0
            return 0 if ok else 1

        controller.poll_pending_commands(
            interval=args.interval,
            clear=not args.no_clear,
            max_idle=args.max_idle,
        )
        return 0
    except KeyboardInterrupt:
        logging.info("已退出轮询")
        return 0
    except Exception as exc:  # pragma: no cover - 防御性日志
        logging.exception("执行过程中出现异常: %s", exc)
        return 1


if __name__ == "__main__":
    sys.exit(main())
