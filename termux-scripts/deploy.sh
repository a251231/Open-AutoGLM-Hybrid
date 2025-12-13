#!/usr/bin/env bash

# Open-AutoGLM 混合方案 - 一键部署脚本（通用环境）
# 版本: 1.2.0

set -euo pipefail

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
print_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }

print_header() {
    echo ""
    echo "============================================================"
    echo "  Open-AutoGLM 混合方案 - 一键部署"
    echo "  版本: 1.2.0"
    echo "============================================================"
    echo ""
}

# 选择包管理器
detect_pkg_manager() {
    if command -v pkg >/dev/null 2>&1; then
        PKG_UPDATE="pkg update -y"
        PKG_INSTALL="pkg install -y"
    elif command -v apt-get >/dev/null 2>&1; then
        PKG_UPDATE="apt-get update -y"
        PKG_INSTALL="apt-get install -y"
    elif command -v apt >/dev/null 2>&1; then
        PKG_UPDATE="apt update -y"
        PKG_INSTALL="apt install -y"
    elif command -v dnf >/dev/null 2>&1; then
        PKG_UPDATE="dnf update -y"
        PKG_INSTALL="dnf install -y"
    elif command -v yum >/dev/null 2>&1; then
        PKG_UPDATE="yum update -y"
        PKG_INSTALL="yum install -y"
    elif command -v pacman >/dev/null 2>&1; then
        PKG_UPDATE="pacman -Sy --noconfirm"
        PKG_INSTALL="pacman -S --noconfirm"
    else
        print_error "未检测到受支持的包管理器（pkg/apt/dnf/yum/pacman），请手动安装依赖：python、git、curl、wget、pip"
        exit 1
    fi
}

check_network() {
    print_info "检查网络连接..."
    if ping -c 1 8.8.8.8 >/dev/null 2>&1; then
        print_success "网络正常"
    else
        print_error "无法访问外网，请检查网络"
        exit 1
    fi
}

update_packages() {
    print_info "更新软件包列表..."
    eval "$PKG_UPDATE"
    print_success "软件包列表更新完成"
}

install_dependencies() {
    print_info "安装必需软件..."

    if ! command -v python >/dev/null 2>&1 && ! command -v python3 >/dev/null 2>&1; then
        print_info "安装 Python..."
        eval "$PKG_INSTALL python"
    else
        PY_VER="$( (command -v python3 && python3 --version) || (command -v python && python --version) )"
        print_success "已检测到 Python: ${PY_VER}"
    fi

    if ! command -v git >/dev/null 2>&1; then
        print_info "安装 Git..."
        eval "$PKG_INSTALL git"
    else
        print_success "已检测到 Git: $(git --version)"
    fi

    eval "$PKG_INSTALL curl wget"
    print_success "必需软件安装完成"
}

install_python_packages() {
    print_info "安装 Python 依赖..."
    PYTHON_BIN="$(command -v python3 || command -v python || true)"
    if [ -z "$PYTHON_BIN" ]; then
        print_error "未找到可用的 python 解释器"
        exit 1
    fi

    "$PYTHON_BIN" -m ensurepip --upgrade >/dev/null 2>&1 || true
    "$PYTHON_BIN" -m pip install --upgrade pip
    "$PYTHON_BIN" -m pip install pillow openai requests
    print_success "Python 依赖安装完成"
}

download_autoglm() {
    print_info "下载 Open-AutoGLM 项目..."
    cd "$HOME"

    if [ -d "Open-AutoGLM" ]; then
        print_warning "检测到已有 Open-AutoGLM 目录"
        read -r -p "是否删除并重新下载? (y/n): " confirm
        if [ "$confirm" = "y" ]; then
            rm -rf Open-AutoGLM
        else
            print_info "跳过重新下载，使用现有目录"
            return
        fi
    fi

    git clone https://github.com/zai-org/Open-AutoGLM.git
    print_success "Open-AutoGLM 下载完成"
}

install_autoglm() {
    print_info "安装 Open-AutoGLM..."
    cd "$HOME/Open-AutoGLM"

    if [ -f "requirements.txt" ]; then
        pip install -r requirements.txt
    fi

    pip install -e .
    print_success "Open-AutoGLM 安装完成"
}

download_hybrid_scripts() {
    print_info "准备混合方案脚本..."
    mkdir -p "$HOME/.autoglm"
    cat > "$HOME/.autoglm/phone_controller.py" << 'PYTHON_EOF'
# 占位文件，如有更新请替换为实际发布版本
pass
PYTHON_EOF
    print_success "混合方案脚本就绪"
}

configure_llm() {
    print_info "配置 LLM 服务提供商..."
    echo "选择提供商:"
    echo "  1) GRS (默认)"
    echo "  2) 硅基流动"
    read -r -p "请输入序号 [1/2]: " provider_choice

    PROVIDER="grs"
    BASE_URL="https://api.grsai.com/v1"
    DEFAULT_MODEL="gpt-4-vision-preview"
    if [ "$provider_choice" = "2" ]; then
        PROVIDER="siliconflow"
        BASE_URL="https://api.siliconflow.cn/v1"
        DEFAULT_MODEL="zai-org/GLM-4.6"
    fi

    read -r -p "请输入 API Key (必填): " api_key
    if [ -z "$api_key" ]; then
        print_error "API Key 不能为空"
        exit 1
    fi

    read -r -p "请输入模型名称(回车使用默认: ${DEFAULT_MODEL}): " model_input
    MODEL="${model_input:-$DEFAULT_MODEL}"

    cat > "$HOME/.autoglm/config.sh" << EOF
#!/usr/bin/env bash
# LLM 配置
export PHONE_AGENT_PROVIDER="${PROVIDER}"
export PHONE_AGENT_BASE_URL="${BASE_URL}"
export PHONE_AGENT_API_KEY="${api_key}"
export PHONE_AGENT_MODEL="${MODEL}"

# AutoGLM Helper 配置
export AUTOGLM_HELPER_URL="http://localhost:8080"
EOF

    if ! grep -q "source ~/.autoglm/config.sh" "$HOME/.bashrc" 2>/dev/null; then
        echo "" >> "$HOME/.bashrc"
        echo "# AutoGLM 配置" >> "$HOME/.bashrc"
        echo "source ~/.autoglm/config.sh" >> "$HOME/.bashrc"
    fi

    # shellcheck source=/dev/null
    source "$HOME/.autoglm/config.sh"
    print_success "LLM 配置完成（提供商: ${PROVIDER}, 模型: ${MODEL}）"
}

create_launcher() {
    print_info "创建启动命令..."
    mkdir -p "$HOME/bin"

    cat > "$HOME/bin/autoglm" << 'LAUNCHER_EOF'
#!/usr/bin/env bash
if [ -f "$HOME/.autoglm/config.sh" ]; then
  # shellcheck source=/dev/null
  source "$HOME/.autoglm/config.sh"
fi
cd "$HOME/Open-AutoGLM" || exit 1
python -m phone_agent.cli
LAUNCHER_EOF

    chmod +x "$HOME/bin/autoglm"

    if ! grep -q 'export PATH=$PATH:$HOME/bin' "$HOME/.bashrc" 2>/dev/null; then
        echo 'export PATH=$PATH:$HOME/bin' >> "$HOME/.bashrc"
    fi

    print_success "启动命令创建完成，重开终端后可直接运行: autoglm"
}

check_helper_app() {
    print_info "请确保已安装并开启 AutoGLM Helper (无障碍服务)。"
    echo "如需检测连接，可手动执行: curl http://localhost:8080/status"
}

show_completion() {
    print_success "部署完成！"
    echo ""
    echo "============================================================"
    echo "  使用方式："
    echo "  1. 确保 AutoGLM Helper 已运行并开启无障碍权限"
    echo "  2. 运行命令: autoglm"
    echo "  3. 输入任务，如：打开淘宝搜索蓝牙耳机"
    echo ""
    echo "  配置文件: $HOME/.autoglm/config.sh"
    echo "  启动命令: autoglm"
    echo "============================================================"
}

main() {
    print_header
    detect_pkg_manager
    check_network
    update_packages
    install_dependencies
    install_python_packages
    download_autoglm
    install_autoglm
    download_hybrid_scripts
    configure_llm
    create_launcher
    check_helper_app
    show_completion
}

main
