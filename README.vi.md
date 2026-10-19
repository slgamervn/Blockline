<div align="center">

# Blockline

### Không gian làm việc di động dành cho nhà phát triển add-on Minecraft Bedrock.

[![Build APK](https://github.com/slgamervn/Blockline/actions/workflows/build-apk.yml/badge.svg)](https://github.com/slgamervn/Blockline/actions/workflows/build-apk.yml)
[![Latest Release](https://img.shields.io/github/v/release/slgamervn/Blockline?display_name=tag&color=3dba7a)](https://github.com/slgamervn/Blockline/releases/latest)

[**Tải APK**](https://github.com/slgamervn/Blockline/releases/latest/download/Blockline.apk) · [Trang giới thiệu](https://slgamervn.github.io/Blockline/) · [Mã nguồn](https://github.com/slgamervn/Blockline)

[English README](README.md)

</div>

---

## Blockline là gì?

Blockline là ứng dụng Android hướng đến việc đọc, tổ chức và chỉnh sửa tệp add-on của **Minecraft Bedrock** ngay trên điện thoại. Dự án ưu tiên quy trình làm việc cục bộ: pack được sao chép vào vùng lưu trữ riêng của ứng dụng và không bị tải lên máy chủ.

Blockline đang trong giai đoạn phát triển. Bản hiện tại tập trung vào nền tảng quản lý dự án, trình duyệt tệp và trình chỉnh sửa cơ bản. Các chức năng kiểm tra chuyên sâu, generator, phân tích dependency và Script API Reference sẽ được triển khai ở những phase tiếp theo.

## Tính năng hiện có

- **Nhập pack:** mở tệp `.mcpack` và `.mcaddon` bằng trình chọn tệp của Android.
- **Quản lý dự án:** tạo project mẫu, mở, đổi tên, sao chép và xóa project.
- **Tổng quan pack:** đọc metadata từ `manifest.json` và mở manifest để chỉnh sửa.
- **File Explorer:** duyệt thư mục; tạo, đổi tên, di chuyển và xóa tệp/thư mục; đánh dấu yêu thích.
- **Trình chỉnh sửa:** nhiều tab, tô màu cú pháp cơ bản, Undo/Redo, định dạng JSON, tìm kiếm và tự lưu.
- **Giải nén an toàn hơn:** kiểm tra đường dẫn trong archive để hạn chế lỗi ZIP-slip.
- **Sao lưu và khôi phục:** tạo backup cục bộ và khôi phục bản gần nhất.
- **Xuất pack:** xuất Behavior Pack, Resource Pack hoặc gói kết hợp nếu project có đủ pack tương ứng.
- **Giao diện di động:** Material 3, theme tối/sáng/theo hệ thống và tùy chỉnh cỡ chữ editor.

## Tiến độ phát triển

| Phase | Phạm vi | Trạng thái |
| --- | --- | --- |
| **1 — Nền tảng** | Dự án cục bộ, import/export, explorer, editor, backup, điều hướng | Đang hoàn thiện |
| **2 — Tìm kiếm và kiểm tra** | Index nội dung, UUID/dependency/reference, báo cáo lỗi | Dự kiến |
| **3 — Generator và snippet** | Tạo tài nguyên pack, snippet và template | Dự kiến |
| **4 — Workflow nâng cao** | Diff, thay identifier hàng loạt, dependency viewer, Script API Reference | Dự kiến |

### Giới hạn hiện tại

- Metadata đang được lưu bằng JSON riêng và preferences; Room, Hilt và Clean Architecture đầy đủ chưa được triển khai.
- Syntax highlighting hiện là bản cơ bản, chưa phải parser ngôn ngữ đầy đủ.
- Search hiện lọc theo đường dẫn tệp; tìm nội dung, identifier và UUID thuộc Phase 2.
- Chưa có màn hình preview chuyên biệt cho từng loại tài nguyên Bedrock.
- Vẫn cần bổ sung kiểm thử tự động và kiểm tra trên thiết bị thực trước khi xem là bản phát hành ổn định.

## Cài đặt

1. Mở [GitHub Releases mới nhất](https://github.com/slgamervn/Blockline/releases/latest).
2. Tải tệp **Blockline.apk**.
3. Mở APK trên thiết bị Android và làm theo hướng dẫn của hệ thống. Android có thể yêu cầu cho phép cài đặt từ ứng dụng tải tệp.
4. Nếu không muốn cài APK, bạn có thể build ứng dụng từ source bằng Android Studio.

Bản phát hành hiện tại là **debug APK** được tạo từ GitHub Actions, chưa phải bản phát hành trên Google Play.

## Build từ source

### Yêu cầu

- Android Studio và Android SDK Platform 35
- JDK 17
- Gradle 8.10.2
- Android 8.0 (API 26) trở lên nếu chạy trên thiết bị

### Các bước

```bash
git clone https://github.com/slgamervn/Blockline.git
cd Blockline
gradle assembleDebug
```

APK được tạo tại:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Hoặc mở repository bằng Android Studio, đồng bộ Gradle rồi chạy cấu hình `app`. Bạn cũng có thể vào **Actions → Build APK** để xem log và tải artifact.

## Công nghệ

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Kotlin Coroutines
- Android Storage Access Framework (SAF)
- App-private storage

## Quyền riêng tư và bảo mật

- Nội dung pack được lưu trong vùng riêng của ứng dụng.
- Phase 1 không yêu cầu quyền `INTERNET` và không tải nội dung project lên mạng.
- Không tích hợp analytics hoặc tracking.
- Giải nén archive có kiểm tra đường dẫn để hạn chế ZIP-slip.

## Đóng góp và báo lỗi

Khi báo lỗi, vui lòng cung cấp phiên bản Android, thiết bị, build/tag đang dùng, các bước tái hiện và hành vi mong đợi/thực tế. Nếu gửi pack mẫu hoặc log, hãy loại bỏ dữ liệu riêng tư trước khi chia sẻ.

## License

Repository hiện chưa có tệp license. Cho đến khi maintainer bổ sung license, không nên mặc định rằng việc mã nguồn được công khai đồng nghĩa với quyền tái sử dụng hoặc phân phối lại.

---

<div align="center">Được xây dựng cho cộng đồng tạo add-on Minecraft Bedrock trên thiết bị di động.</div>
