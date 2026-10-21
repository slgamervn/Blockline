<div align="center">

# Blockline

### Không gian làm việc trên điện thoại dành cho người tạo add-on Minecraft Bedrock.

**Nhập · Duyệt · Sửa · Kiểm tra · Tạo · So sánh · Xuất**

[![Build APK](https://github.com/slgamervn/Blockline/actions/workflows/build-apk.yml/badge.svg?branch=main)](https://github.com/slgamervn/Blockline/actions/workflows/build-apk.yml)
[![Latest Release](https://img.shields.io/github/v/release/slgamervn/Blockline?display_name=tag&color=3dba7a)](https://github.com/slgamervn/Blockline/releases/latest)

[**Tải APK**](https://github.com/slgamervn/Blockline/releases/latest) · [Trang giới thiệu](https://slgamervn.github.io/Blockline/) · [Mã nguồn](https://github.com/slgamervn/Blockline)

[English README](README.md)

</div>

---

## Blockline là gì?

Blockline là ứng dụng Android-native để đọc, tổ chức và chỉnh sửa **add-on Minecraft Bedrock** trực tiếp trên điện thoại. Ứng dụng dùng Kotlin, Jetpack Compose, Material 3 và lưu bản sao dự án trong vùng lưu trữ riêng của ứng dụng; việc nhập/xuất dùng trình chọn tệp của Android.

Lộ trình ban đầu đã được chuyển sang theo dõi theo từng nhóm tính năng. Website và danh sách tính năng mô tả những phần hiện đã triển khai; các mục ghi cần QA vẫn phải được kiểm tra trên thiết bị Android thật và pack Bedrock đại diện. CI thành công chỉ xác nhận các kiểm tra tự động đã cấu hình và quy trình build APK của commit đó chạy qua.

## Tính năng

### Quản lý dự án
- Nhập `.mcpack`, `.mcaddon` và archive tương thích ZIP qua trình chọn tệp Android.
- Lưu bản sao project trong bộ nhớ riêng của ứng dụng; tạo project mẫu, chuyển project, đổi tên, nhân bản và xóa.
- Xem manifest và metadata pack; tạo bản sao lưu cục bộ hoặc khôi phục backup gần nhất.
- Xuất Behavior Pack, Resource Pack hoặc add-on kết hợp; tạo backup trước khi export.
- Giải nén archive có kiểm tra đường dẫn đích để giảm nguy cơ ZIP-slip và xử lý lỗi archive an toàn hơn.

### Explorer và editor
- Duyệt và quản lý tệp/thư mục: tạo, đổi tên, di chuyển, xóa, đánh dấu yêu thích và mở lại tệp gần đây.
- Xem trước metadata và mã nguồn của các loại tài nguyên được nhận diện; đây không phải trình render trong game.
- Chỉnh sửa JSON, JavaScript và `.mcfunction` với nhiều tab, Undo/Redo, tự lưu, tìm kiếm và định dạng JSON giữ comment.
- Có giới hạn với tệp lớn: tệp trên 1 MiB được mở chỉ đọc; tệp vượt ngưỡng cứng do ứng dụng quy định sẽ không được nạp vào editor.
- Tô màu cú pháp hiện là giải pháp gọn nhẹ, chưa phải language server hay parser IDE đầy đủ.

### Tìm kiếm, kiểm tra và sức khỏe pack
- Tìm đường dẫn và nội dung văn bản trong project theo bộ lọc loại tệp; xem dòng khớp và mở kết quả trong editor.
- Kiểm tra cú pháp JSON, trường manifest, định dạng/trùng UUID, đường dẫn script entry, metadata dependency và một số tham chiếu tài nguyên.
- Xem báo cáo lỗi/cảnh báo/thông tin đã bản địa hóa và điểm sức khỏe pack mang tính heuristic. Điểm này chỉ hỗ trợ rà soát, không bảo đảm Minecraft sẽ tải pack thành công.
- Phân tích dependency để phân biệt mục tìm thấy trong project với dependency bên ngoài có thể do Minecraft hoặc pack đã cài cung cấp.

### Generator và snippet
- Tạo manifest Behavior Pack hoặc Resource Pack với UUID mới; generator không ghi đè manifest đã tồn tại.
- Xem và sao chép snippet JavaScript Script API cùng `.mcfunction`.
- Dùng sample pack đi kèm làm điểm khởi đầu.

### Workflow nâng cao và Script API Reference
- So sánh nội dung bằng line-diff engine và xem nhật ký hoạt động cục bộ theo từng dự án. Kiểm tra bản cập nhật APK ổn định từ trang GitHub Releases chính thức.
- Xem trước đổi tên tệp và thay thế văn bản hàng loạt trước khi áp dụng, có kiểm tra xung đột, backup và khôi phục khi thao tác lỗi.
- Kiểm tra dependency trong manifest, nguồn/đích, sai khác phiên bản và UUID bên ngoài chưa được phân giải trong project.
- Tra cứu TypeScript declaration của các npm package được hỗ trợ. Repository tra cứu có thể lấy metadata phiên bản từ npm, lấy declaration từ jsDelivr, cache phiên bản/mã nguồn để dùng lại khi offline và fallback sang snapshot đi kèm. Các declaration chưa parse được vẫn được giữ ở dạng raw thay vì bị loại bỏ.

## Điều hướng

Ứng dụng có năm mục chính: **Projects, Explorer, Search, Tools và More**. Có tùy chọn giao diện và cỡ chữ editor. Hầu hết tác vụ chỉnh sửa pack chạy cục bộ; làm mới Script API từ nguồn online cần mạng, còn dữ liệu đã cache hoặc snapshot đi kèm có thể dùng offline.

## Build và kiểm thử

Yêu cầu:
- Android Studio và Android SDK Platform 35
- JDK 17
- Gradle 8.14.6
- Android 8.0 (API 26) trở lên để chạy ứng dụng

```bash
git clone https://github.com/slgamervn/Blockline.git
cd Blockline
gradle testDebugUnitTest
gradle assembleDebug
```

APK debug được tạo tại `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions chạy kiểm tra website và unit test trước khi build và phát hành APK theo phiên bản. Bộ kiểm tra website rà tài nguyên liên kết, landmark và anchor HTML, cú pháp CSS/JavaScript, bản dịch, breakpoint responsive và chế độ giảm chuyển động. Vào **Actions → Build APK** để xem log và tải artifact. Tệp phát hành có tên `Blockline-v<version>.apk`; APK release đã ký cần signing secrets của repository, nếu không workflow tạo APK debug. APK debug dùng khóa khác có thể không cài đè cập nhật lên bản đã ký bằng khóa khác.

## Nguyên tắc hiệu năng và độ tin cậy

Blockline áp dụng các hướng dẫn Compose phù hợp với ứng dụng: lazy layout cho danh sách có thể lớn, stable key cho danh sách thay đổi, chuyển công việc nặng khỏi các lần UI recomposition thường xuyên và giới hạn rõ ràng với tệp editor lớn. Các kỹ thuật này giúp giảm công việc dư thừa nhưng không thay thế việc profiling trên thiết bị đại diện.

## Quyền riêng tư và bảo mật

- Pack nhập vào được sao chép vào vùng lưu trữ riêng của ứng dụng và không được tải lên trong quy trình chỉnh sửa thông thường.
- Làm mới Script API là tính năng mạng có chủ đích, dùng để lấy metadata npm và declaration.
- Giải nén archive có kiểm tra đường dẫn đích để giảm nguy cơ ZIP-slip.
- Không đăng pack riêng tư hoặc log có thông tin nhạy cảm lên issue công khai.

## Giới hạn hiện tại

- Validator tĩnh chưa thay thế đầy đủ schema Mojang và không thể bảo đảm tương thích trong game.
- Preview tài nguyên chỉ hiển thị metadata/mã nguồn, không render như trong game.
- Bộ phân tích Script API declaration ưu tiên khả năng chịu lỗi, không phải TypeScript compiler đầy đủ; declaration thô được giữ lại khi không phân tích được cấu trúc.
- Vẫn cần QA trên thiết bị thật, profiling hiệu năng và kiểm thử hồi quy trên nhiều phiên bản Android cùng các pack mẫu trước khi coi bất kỳ build nào là bản production.

## Đóng góp và báo lỗi

Khi báo lỗi, hãy cung cấp phiên bản Blockline, phiên bản Android/thiết bị, các bước tái hiện, hành vi mong đợi và thực tế cùng log liên quan. Xóa nội dung pack riêng tư và thông tin xác thực trước khi chia sẻ.

## License

Mã nguồn Blockline được cấp phép theo [MIT License](LICENSE). Các dependency và tài nguyên của bên thứ ba có thể chịu giấy phép riêng.

---

<div align="center">

Được xây dựng cho cộng đồng tạo add-on Minecraft Bedrock trên thiết bị di động.

[Website](https://slgamervn.github.io/Blockline/) · [Releases](https://github.com/slgamervn/Blockline/releases) · [Issues](https://github.com/slgamervn/Blockline/issues)

</div>
