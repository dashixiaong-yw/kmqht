"""二维码工具"""

import base64
import io


def generate_qr_base64(data: str, box_size: int = 8, border: int = 2) -> str:
    """生成二维码图片的base64编码

    Args:
        data: 二维码内容
        box_size: 每个像素点的大小（默认8）
        border: 边框宽度（默认2）

    Returns:
        base64编码的图片数据（不含data:image前缀）
    """
    import qrcode
    img = qrcode.make(data, version=1, box_size=box_size, border=border)
    buffer = io.BytesIO()
    img.save(buffer, format="PNG")
    return base64.b64encode(buffer.getvalue()).decode("utf-8")
