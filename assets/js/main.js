(() => {
  'use strict';

  const root = document.documentElement;
  const body = document.body;
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const header = document.querySelector('.site-header');
  const menu = document.querySelector('#mobile-menu');
  const menuToggle = document.querySelector('.menu-toggle');
  const menuClose = document.querySelector('.menu-close');
  const dialog = document.querySelector('#browser-dialog');
  const menuFocusable = 'a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])';
  let menuIsOpen = false;
  let previousFocus = null;
  let scrollFrame = 0;


  let currentLanguage = 'vi';
  let scrollDirection = 1;
  let previousScrollY = window.scrollY;
  const englishText = {
    "Bỏ qua đến nội dung":"Skip to content",
    "Tính năng":"Features","Trải nghiệm":"Explore","Lộ trình":"Roadmap","Tải APK":"Download APK",
    "BLOCKLINE / ĐIỀU HƯỚNG":"BLOCKLINE / NAVIGATION","Câu hỏi thường gặp":"Frequently asked questions","Hỏi đáp":"FAQs","Tải Blockline APK":"Download Blockline APK",
    "Android 8.0 trở lên · APK mới nhất":"Android 8.0+ · Latest APK",
    "Android 8.0 trở lên":"Android 8.0 or later","Bản phát hành mới nhất":"Latest release",
    "CÔNG CỤ LÀM PACK TRÊN ANDROID":"A PACK TOOLKIT FOR ANDROID","Làm chủ pack.":"Own your pack.","Ngay trên điện thoại.":"Right on your phone.",
    "Mở, chỉnh sửa và chuẩn bị add-on Minecraft Bedrock ngay trên thiết bị Android. Từ cấu trúc thư mục đến những dòng JSON — tất cả trong một không gian làm việc gọn gàng.":"Open and prepare Minecraft Bedrock add-ons on Android. From organizing files to editing JSON, keep your pack workflow in one focused workspace.",
    "Khám phá cách hoạt động":"See how it works","Mới nhất":"Latest","Bản phát hành":"Release","Yêu cầu":"Required","Dùng ngoại tuyến":"Works offline",
    "GIAO DIỆN MINH HỌA":"INTERFACE CONCEPT","ĐANG CHỈNH SỬA":"CURRENTLY EDITING","Đã lưu cục bộ":"Saved locally","Dự án":"Projects","Tệp":"Files","Tìm":"Search","Thêm":"More","Chỉ mang tính minh họa":"For illustrative purposes only",
    "MỘT KHÔNG GIAN LÀM VIỆC KHÁC":"A DIFFERENT KIND OF WORKSPACE",
    "Pack không chỉ là một tệp để nhập vào game. Đó là cấu trúc, dữ liệu và những chi tiết bạn muốn tự tay hoàn thiện.":"A pack is more than a file you import into a game. It is structure, data, and details you shape yourself.",
    "Blockline đưa công việc ấy lên điện thoại — để bạn có thể duyệt, chỉnh sửa, sao lưu và xuất pack ngay tại nơi mình đang làm việc.":"Blockline brings that workflow to your phone, so you can browse, edit, back up, and export packs wherever you work.",
    "01 — TỪ CẤU TRÚC ĐẾN BẢN XUẤT":"01 — FROM STRUCTURE TO EXPORT","Thiết kế cho quy trình làm pack trên Android":"Designed for Android pack workflows",
    "CÔNG CỤ THỰC DỤNG":"TOOLS THAT DO THE WORK","Tập trung vào phần việc":"Focus on the work","quan trọng nhất.":"that matters most.",
    "Không cần biến điện thoại thành máy tính. Blockline tập trung vào những thao tác thường gặp khi làm việc với pack.":"You shouldn’t need a desktop for everyday pack work. Blockline brings the essential tools to your Android device.",
    "01 / CẤU TRÚC":"01 / STRUCTURE","Một pack. Rõ từng tệp.":"One pack. Every file, organized.",
    "Nhập .mcpack hoặc .mcaddon, tự tách Behavior Pack và Resource Pack, rồi duyệt cây thư mục có thể thu gọn. Tạo, đổi tên, di chuyển, xóa và đánh dấu tệp yêu thích ngay trong dự án.":"Import .mcpack or .mcaddon files, separate Behavior and Resource Packs, and browse a collapsible file tree. Create, rename, move, delete, and favorite files in your project.",
    "Nhập pack có bảo vệ zip-slip":"Safer archive imports with Zip Slip protection","Cây tệp thu gọn và yêu thích":"Collapsible file tree and favorites","Tạo, đổi tên, di chuyển và xóa tệp":"Create, rename, move, and delete files",
    "Đang chọn":"Selected file","Nhấn vào tệp để đổi lựa chọn minh họa.":"Select a file to change this preview.",
    "02 / CHỈNH SỬA":"02 / EDITING","Mã nguồn dễ đọc hơn.":"Code that is easier to read.",
    "Trình soạn thảo hỗ trợ tô màu cú pháp cho JSON, JavaScript và .mcfunction, nhiều tab, hoàn tác/làm lại, tìm trong tệp, tự lưu và định dạng JSON có giữ comment.":"The editor provides syntax highlighting for JSON, JavaScript, and .mcfunction, plus multiple tabs, undo/redo, find-in-file, autosave, and JSON formatting that preserves comments.",
    "Chuyển tab không mất mạch làm việc":"Switch tabs without losing your place","Undo / redo và tìm nội dung":"Undo / redo and find text","Định dạng JSON giữ comment":"Format JSON while preserving comments","Mô phỏng trình soạn thảo":"Editor preview",
    "03 / CẤU HÌNH PACK":"03 / PACK DETAILS","Đọc manifest có ngữ cảnh.":"Understand your manifest.",
    "Xem UUID, module và dependency trong manifest để hiểu pack đang khai báo những gì. Sao lưu và khôi phục dự án khi cần, trước khi xuất bản thử nghiệm.":"Inspect UUIDs, modules, and dependencies declared in the manifest. Back up and restore your project before testing an export.",
    "Thông tin header và module":"Header and module details","UUID và dependency":"UUIDs and dependencies","Sao lưu / khôi phục cục bộ":"Local backup and restore","ĐỌC TỪ TỆP":"READ FROM FILE",
    "Giá trị trên là dữ liệu minh họa, không phải manifest của pack thật.":"Values shown are illustrative, not from a real pack manifest.",
    "XEM QUY TRÌNH":"SEE THE WORKFLOW","Từ gói nén đến bản xuất.":"From archive to export.",
    "Chọn từng bước để xem minh họa cách Blockline hỗ trợ một quy trình làm pack thường gặp.":"Choose a step to explore how Blockline can support a typical pack workflow.",
    "Các bước trong quy trình Blockline":"Steps in the Blockline workflow","Mở pack":"Open pack","Nhập .mcpack / .mcaddon":"Import .mcpack / .mcaddon",
    "Sửa tệp":"Edit files","Duyệt và chỉnh sửa nội dung":"Browse and edit content","Đọc manifest":"Read manifest","Xem UUID và dependency":"View UUIDs and dependencies","Xuất pack":"Export pack","BP, RP hoặc cả hai":"BP, RP, or both",
    "Nhập gói từ thiết bị. Blockline nhận diện cấu trúc và tách BP/RP khi có trong gói.":"Import an archive from your device. Blockline detects the structure and separates BP/RP when both are included.",
    "Sửa tệp văn bản trong editor, chuyển giữa nhiều tab và định dạng JSON có giữ comment.":"Edit text files, switch between tabs, and format JSON while preserving comments.",
    "Đã đọc":"Read","Mô phỏng phần thông tin đọc được, không phải công cụ xác thực toàn bộ pack.":"Illustrative manifest information, not a full pack validator.",
    "Đọc thông tin manifest để xem định danh và các dependency được khai báo.":"Inspect identifiers and dependencies declared in the manifest.",
    "Gói .mcaddon":".mcaddon archive","Chọn xuất Behavior Pack, Resource Pack hoặc gói kết hợp để kiểm tra trong Minecraft.":"Export a Behavior Pack, Resource Pack, or combined archive for testing in Minecraft.",
    "NÓI RÕ GIỚI HẠN":"CLEAR EXPECTATIONS","Đúng việc. Đúng kỳ vọng.":"The right tool. Realistic expectations.",
    "Blockline là công cụ làm việc với tệp pack trên Android — không phải trình thay thế toàn bộ bộ công cụ phát triển Minecraft.":"Blockline helps you work with pack files on Android; it is not a replacement for the entire Minecraft development toolchain.",
    "Bảng so sánh phạm vi sử dụng Blockline":"Blockline capability comparison","Nhu cầu":"Task","Blockline hiện hỗ trợ":"Available in Blockline","Giới hạn cần biết":"Limitations",
    "Mở và quản lý pack":"Open and manage packs","Nhập .mcpack / .mcaddon, duyệt BP/RP và quản lý tệp.":"Import .mcpack / .mcaddon, browse BP/RP, and manage files.",
    "Chỉ dùng trên Android 8.0 trở lên.":"Android 8.0 or later only.","Sửa nội dung":"Edit content",
    "Editor JSON, JavaScript, .mcfunction; tab, undo/redo và tự lưu.":"JSON, JavaScript, and .mcfunction editor; tabs, undo/redo, and autosave.",
    "Tệp trên 1 MB chỉ mở ở chế độ đọc; tệp trên 5 MB không mở được.":"Files over 1 MB are read-only; files over 5 MB cannot be opened.",
    "Đọc UUID, module và dependency.":"Read UUIDs, modules, and dependencies.","Đọc manifest không đồng nghĩa kiểm tra toàn bộ tính đúng đắn của pack.":"Reading a manifest does not validate the entire pack.",
    "Kiểm tra pack tự động":"Automated pack validation","Kiểm tra tĩnh JSON, manifest, UUID, script entry, dependency và một số tham chiếu.":"Static checks for JSON, manifests, UUIDs, script entries, dependencies, and selected references.","Không thay thế schema validator đầy đủ của Mojang.":"Does not replace the full Mojang schema validator.",
    "Generator và snippet":"Generators and snippets","Tạo manifest với UUID mới và sao chép snippet.":"Generate manifests with fresh UUIDs and copy snippets.","Chưa phải trình tạo entity/item/block hoàn chỉnh.":"Not a complete entity/item/block generator.",
    "Workflow nâng cao":"Advanced workflows","Diff, xem trước đổi tên/thay thế hàng loạt, dependency viewer và Script API reference.":"Diff, preview batch rename/replacements, dependency viewer, and Script API reference.","Parser Script API không phải TypeScript compiler đầy đủ; cần QA trên pack thật.":"The Script API parser is not a full TypeScript compiler; test it with real packs.",
    "Bảng mô tả phạm vi tính năng của bản hiện tại.":"This table describes the current feature scope.",
    "TRẠNG THÁI SẢN PHẨM":"PRODUCT STATUS","Từ nền tảng đến workflow nâng cao.":"From foundation to advanced workflows.","Các nhóm tính năng dưới đây đã có phần triển khai trong mã nguồn. Mức độ tương thích thực tế vẫn cần được kiểm chứng với thiết bị và pack cụ thể.":"The feature groups below have implementations in the source. Real-world compatibility still needs verification with specific devices and packs.",
    "ĐÃ TRIỂN KHAI · CẦN QA":"IMPLEMENTED · QA NEEDED","Quản lý dự án và chỉnh sửa":"Project management and editing","Nhập/xuất pack, explorer, nhiều tab editor, tự lưu, backup/restore và xử lý archive an toàn hơn.":"Pack import/export, explorer, multi-tab editor, autosave, backup/restore, and safer archive handling.",
    "Tìm kiếm và kiểm tra pack":"Search and pack validation","Tìm đường dẫn/nội dung, kiểm tra JSON và manifest, UUID, script entry, dependency cùng một số tham chiếu tài nguyên.":"Search paths and file contents; check JSON, manifests, UUIDs, script entries, dependencies, and selected resource references.",
    "Generator và snippet":"Generators and snippets","Tạo manifest với UUID mới, sao chép snippet JavaScript/Script API và Minecraft function.":"Generate manifests with fresh UUIDs and copy JavaScript/Script API and Minecraft function snippets.",
    "Diff, dependency và Script API":"Diff, dependencies, and Script API","So sánh văn bản, xem trước thay thế hàng loạt, phân tích dependency và tra cứu declaration có cache/offline fallback.":"Compare text, preview batch replacement, analyze dependencies, and browse declarations with caching and offline fallback.",
    "BẮT ĐẦU":"GET STARTED","Ba bước để bắt đầu.":"Three steps to get started.",
    "Không cần bước build cho website này. Với ứng dụng, hãy tải APK và cài trên thiết bị Android tương thích.":"There is no website build step. For the app, download the APK and install it on a compatible Android device.",
    "Nhấn nút tải để lấy APK Blockline của bản phát hành mới nhất.":"Use the download button to get the latest Blockline APK release.",
    "Cài trên Android":"Install on Android","Thiết bị cần chạy Android 8.0 trở lên. Chỉ cài APK từ nguồn bạn tin cậy.":"Your device needs Android 8.0 or later. Only install APKs from sources you trust.",
    "Mở pack của bạn":"Open your pack","Chọn tệp .mcpack hoặc .mcaddon từ thiết bị để bắt đầu làm việc.":"Choose a .mcpack or .mcaddon file on your device to get started.",
    "CÂU HỎI THƯỜNG GẶP":"FREQUENTLY ASKED QUESTIONS","Trước khi tải.":"Before you download.","Thông tin rõ ràng về nền tảng, tệp hỗ trợ và giới hạn hiện tại.":"Clear information about supported devices, files, and current limitations.",
    "Blockline hỗ trợ thiết bị nào?":"Which devices does Blockline support?",
    "Blockline là ứng dụng Android, yêu cầu Android 8.0 trở lên. Liên kết tải xuống luôn trỏ tới APK của bản phát hành mới nhất trên GitHub.":"Blockline is an Android app requiring Android 8.0 or later. The download link always points to the latest APK release on GitHub.",
    "Có cần kết nối mạng để chỉnh sửa pack không?":"Do I need an internet connection to edit packs?",
    "Các công cụ chính được mô tả có thể dùng ngoại tuyến. Trang web chỉ là trang giới thiệu và cung cấp liên kết tải APK.":"The main tools described can be used offline. This website is a product page with an APK download link.",
    "Blockline có thể mở những loại tệp nào?":"Which file types can Blockline open?",
    "Ứng dụng nhận .mcpack và .mcaddon; editor hỗ trợ tệp văn bản JSON, JavaScript và .mcfunction. Tệp trên 1 MB chỉ mở ở chế độ đọc; tệp trên 5 MB không mở được.":"The app accepts .mcpack and .mcaddon; the editor supports JSON, JavaScript, and .mcfunction text files. Files over 1 MB are read-only, and files over 5 MB cannot be opened.",
    "Blockline đã kiểm tra pack tự động chưa?":"Does Blockline validate packs automatically yet?",
    "Có kiểm tra tĩnh cho cú pháp JSON, một số trường manifest, UUID, script entry, dependency và một số tham chiếu tài nguyên. Đây không phải schema validator đầy đủ của Mojang và không bảo đảm pack sẽ chạy đúng trong game.":"Static checks cover JSON syntax, selected manifest fields, UUIDs, script entries, dependencies, and some resource references. This is not a complete Mojang schema validator and cannot guarantee the pack will run correctly in-game.",
    "APK có sẵn trong kho mã nguồn không?":"Is the APK stored in the source repository?",
    "Tải APK ở đâu?":"Where can I download the APK?","Nút tải trên trang dẫn đến tệp APK của bản phát hành mới nhất trên GitHub. Hãy kiểm tra tên dự án và nguồn phát hành trước khi cài đặt.":"The download button links to the APK attached to the latest GitHub release. Confirm that you’re on the official Blockline project page before installing.",
    "Pack của bạn.":"Your pack.","Quy trình của bạn.":"Your workflow.",
    "Mở, chỉnh sửa, sao lưu và xuất pack Minecraft Bedrock ngay trên điện thoại Android.":"Open, edit, back up, and export Minecraft Bedrock packs right on your Android phone.",
    "Xem mã nguồn":"View source code","Công cụ chính dùng ngoại tuyến":"Core tools work offline",
    "Công cụ làm việc với add-on Minecraft Bedrock trên Android.":"A workspace for Minecraft Bedrock add-ons on Android.",
    "SẢN PHẨM":"PRODUCT","HỖ TRỢ":"SUPPORT","Mã nguồn trên GitHub ↗":"Source code on GitHub ↗","Báo lỗi ↗":"Report a bug ↗",
    "GHI CHÚ":"NOTES","Blockline là dự án độc lập. Không liên kết với Mojang hay Microsoft.":"Blockline is an independent project and is not affiliated with Mojang or Microsoft.",
    "Chuyển giao diện sáng / tối":"Toggle light / dark theme","Được thiết kế cho người làm pack trên Android.":"Designed for Android pack creators.",
    "Về đầu trang ↑":"Back to top ↑","Giới hạn trình soạn thảo: tệp lớn hơn 1 MB chỉ mở ở chế độ đọc; tệp lớn hơn 5 MB không mở được.":"Editor limit: files larger than 1 MB are read-only; files larger than 5 MB cannot be opened.",
    "Các khung giao diện trên trang là mô phỏng HTML/CSS, không phải ảnh chụp hoặc bản ghi màn hình ứng dụng thật.":"The interface panels are HTML/CSS concepts, not real app screenshots or screen recordings.",
    "HƯỚNG DẪN TẢI APK":"APK DOWNLOAD GUIDE","Mở trang này bằng Chrome.":"Open this page in Chrome.",
    "Trình duyệt bên trong Zalo, Facebook hoặc Messenger có thể hạn chế tải tệp APK. Hãy mở trang bằng Chrome rồi thử tải lại.":"In-app browsers in Zalo, Facebook, or Messenger may restrict APK downloads. Open this page in Chrome and try again.",
    "Chép liên kết trang":"Copy page link","Đã hiểu":"Got it"
  };
  const vietnameseText = {
    "PROJECT EXPLORER":"CÂY THƯ MỤC",
    "LOCAL FILES":"TỆP CỤC BỘ",
    "EDITOR / MY ADD-ON":"TRÌNH SOẠN THẢO / MY ADD-ON",
    "HTML / CSS CONCEPT":"MÔ PHỎNG HTML / CSS",
    "01 / EDITOR":"01 / TRÌNH SOẠN THẢO",
    "IMPORT / PACK FILE":"NHẬP PACK / TỆP",
    "EDITOR / TEXT FILE":"SOẠN THẢO / TỆP VĂN BẢN",
    "MANIFEST / DETAILS":"MANIFEST / CHI TIẾT",
    "EXPORT / PACK":"XUẤT PACK",
    "WORKSPACE":"KHÔNG GIAN LÀM VIỆC",
    "LOCAL":"CỤC BỘ",
    "MANIFEST INSPECTOR":"KIỂM TRA MANIFEST",
    "2 PACKS":"2 GÓI",
    "FAQ":"Hỏi đáp",
    "Offline":"Ngoại tuyến",
    "LATEST RELEASE":"BẢN PHÁT HÀNH MỚI NHẤT",
    "HEADER UUID":"UUID PHẦN ĐẦU",
    "MODULE TYPE":"LOẠI MODULE",
    "MODULE":"MODULE",
    "DEPENDENCIES":"PHẦN PHỤ THUỘC",
    "DEPENDENCY":"PHỤ THUỘC",
    "Hướng dẫn tải APK":"HƯỚNG DẪN TẢI APK"
  };

  const localizedTextNodes = [];
  const textWalker = document.createTreeWalker(body, NodeFilter.SHOW_TEXT);
  while (textWalker.nextNode()) {
    const node = textWalker.currentNode;
    if (!node.nodeValue.trim() || node.parentElement?.closest('script,style')) continue;
    localizedTextNodes.push({ node, original: node.nodeValue });
  }

  const translateTextNode = (original, language) => {
    const key = original.trim();
    const dictionary = language === 'en' ? englishText : vietnameseText;
    const value = dictionary[key];
    if (!value) return original;
    const leading = original.match(/^\\s*/)?.[0] || '';
    const trailing = original.match(/\\s*$/)?.[0] || '';
    return leading + value + trailing;
  };

  const syncLocalizedControls = () => {
    const english = currentLanguage === 'en';
    const theme = root.dataset.theme === 'light' ? 'light' : 'dark';
    document.querySelectorAll('.theme-toggle').forEach((button) => {
      button.setAttribute('aria-label', english
        ? (theme === 'light' ? 'Switch to dark theme' : 'Switch to light theme')
        : (theme === 'light' ? 'Chuyển sang giao diện tối' : 'Chuyển sang giao diện sáng'));
      if (button.hasAttribute('title')) button.title = english ? 'Toggle theme' : 'Chuyển giao diện';
    });
    document.querySelectorAll('.brand').forEach((brand) => brand.setAttribute('aria-label', english ? 'Blockline — back to top' : 'Blockline — về đầu trang'));
    document.querySelector('.desktop-nav')?.setAttribute('aria-label', english ? 'Main navigation' : 'Điều hướng chính');
    menu?.setAttribute('aria-label', english ? 'Mobile navigation menu' : 'Menu điều hướng điện thoại');
    menuToggle?.setAttribute('aria-label', menuIsOpen ? (english ? 'Close navigation menu' : 'Đóng menu điều hướng') : (english ? 'Open navigation menu' : 'Mở menu điều hướng'));
    menuClose?.setAttribute('aria-label', english ? 'Close menu' : 'Đóng menu');
    document.querySelector('.hero-visual')?.setAttribute('aria-label', english ? 'Blockline interface concept, not a real app screenshot' : 'Mô phỏng giao diện Blockline, không phải ảnh chụp ứng dụng thật');
    document.querySelector('.hero-facts')?.setAttribute('aria-label', english ? 'Product information' : 'Thông tin sản phẩm');
    document.querySelector('.file-explorer')?.setAttribute('aria-label', english ? 'Interactive file tree concept' : 'Minh họa cây thư mục tương tác');
    document.querySelector('.editor-tab-buttons')?.setAttribute('aria-label', english ? 'Choose a preview file' : 'Chọn tệp xem trước');
    document.querySelector('.demo-tabs')?.setAttribute('aria-label', english ? 'Steps in the Blockline workflow' : 'Các bước trong quy trình Blockline');
    document.querySelector('.comparison-wrap')?.setAttribute('aria-label', english ? 'Blockline capability comparison' : 'Bảng so sánh phạm vi sử dụng Blockline');
    document.querySelector('.dialog-close')?.setAttribute('aria-label', english ? 'Close download guide' : 'Đóng hướng dẫn');
    const selectedDemoKey = document.querySelector('[data-demo-tab][aria-selected="true"]')?.dataset.demoTab;
    const activeDemoLabel = document.querySelector('#demo-screen-label');
    const localizedDemoLabels = {
      import: ['NHẬP PACK / TỆP', 'IMPORT / PACK FILE'],
      edit: ['SOẠN THẢO / TỆP VĂN BẢN', 'EDITOR / TEXT FILE'],
      manifest: ['MANIFEST / CHI TIẾT', 'MANIFEST / DETAILS'],
      export: ['XUẤT PACK', 'EXPORT / PACK']
    };
    if (activeDemoLabel && selectedDemoKey && localizedDemoLabels[selectedDemoKey]) {
      activeDemoLabel.textContent = localizedDemoLabels[selectedDemoKey][english ? 1 : 0];
    }
    const copyStatus = document.querySelector('.copy-status');
    if (copyStatus?.dataset.state === 'copied') copyStatus.textContent = english
      ? 'Link copied. Open Chrome and paste it into the address bar.'
      : 'Đã chép liên kết. Hãy mở Chrome và dán liên kết vào thanh địa chỉ.';
    else if (copyStatus?.dataset.state === 'failed') copyStatus.textContent = english
      ? 'Could not copy automatically. Copy the address from your browser bar.'
      : 'Không thể tự chép liên kết. Hãy sao chép địa chỉ trên thanh trình duyệt.';
    const languageToggle = document.querySelector('[data-language-toggle]');
    if (languageToggle) {
      languageToggle.textContent = english ? 'VI' : 'EN';
      languageToggle.setAttribute('aria-label', english ? 'Switch language to Vietnamese' : 'Chuyển ngôn ngữ sang English');
      languageToggle.title = english ? 'Switch to Vietnamese' : 'Switch to English';
    }
  };

  const setLanguage = (language) => {
    currentLanguage = language === 'en' ? 'en' : 'vi';
    root.lang = currentLanguage;
    localizedTextNodes.forEach(({ node, original }) => {
      if (node.isConnected) node.nodeValue = translateTextNode(original, currentLanguage);
    });
    document.title = currentLanguage === 'en'
      ? 'Blockline — Your pack. Your workflow.'
      : 'Blockline — Làm chủ pack. Ngay trên điện thoại.';
    const description = document.querySelector('meta[name="description"]');
    const ogTitle = document.querySelector('meta[property="og:title"]');
    const ogDescription = document.querySelector('meta[property="og:description"]');
    const ogLocale = document.querySelector('meta[property="og:locale"]');
    if (ogLocale) ogLocale.content = currentLanguage === 'en' ? 'en_US' : 'vi_VN';
    if (description) description.content = currentLanguage === 'en'
      ? 'Blockline is an Android app for opening, editing, inspecting, and exporting Minecraft Bedrock add-ons and resource packs.'
      : 'Blockline là ứng dụng Android giúp mở, chỉnh sửa, kiểm tra và xuất add-on/resource pack Minecraft Bedrock ngay trên điện thoại.';
    if (ogTitle) ogTitle.content = document.title;
    if (ogDescription) ogDescription.content = currentLanguage === 'en'
      ? 'Open, edit, inspect, and export Minecraft Bedrock packs right on Android.'
      : 'Mở, chỉnh sửa, kiểm tra và xuất pack Minecraft Bedrock ngay trên Android.';
    try { localStorage.setItem('blockline-language', currentLanguage); } catch (_) {}
    syncLocalizedControls();
  };

  document.querySelector('[data-language-toggle]')?.addEventListener('click', () => {
    setLanguage(currentLanguage === 'vi' ? 'en' : 'vi');
  });

  const setTheme = (theme) => {
    const next = theme === 'light' ? 'light' : 'dark';
    root.dataset.theme = next;
    try { localStorage.setItem('blockline-theme', next); } catch (_) {}
    document.querySelectorAll('.theme-toggle').forEach((button) => {
      button.setAttribute('aria-pressed', String(next === 'light'));
      button.setAttribute('aria-label', currentLanguage === 'en' ? (next === 'light' ? 'Switch to dark theme' : 'Switch to light theme') : (next === 'light' ? 'Chuyển sang giao diện tối' : 'Chuyển sang giao diện sáng'));
    });
    const themeMeta = document.querySelector('meta[name="theme-color"]');
    if (themeMeta) themeMeta.content = next === 'light' ? '#f4f7f2' : '#101612';
    syncLocalizedControls();
  };

  let savedTheme = '';
  try { savedTheme = localStorage.getItem('blockline-theme') || ''; } catch (_) {}
  if (savedTheme === 'light' || savedTheme === 'dark') setTheme(savedTheme);
  else {
    const lightPreference = window.matchMedia('(prefers-color-scheme: light)').matches;
    root.dataset.theme = lightPreference ? 'light' : 'dark';
    setTheme(root.dataset.theme);
  }
  document.querySelectorAll('.theme-toggle').forEach((button) => {
    button.addEventListener('click', () => setTheme(root.dataset.theme === 'light' ? 'dark' : 'light'));
  });

  const updateScrollChrome = () => {
    scrollFrame = 0;
    header?.classList.toggle('is-scrolled', window.scrollY > 12);
    const maxScroll = root.scrollHeight - window.innerHeight;
    const ratio = maxScroll > 0 ? Math.min(1, Math.max(0, window.scrollY / maxScroll)) : 0;
    const bar = document.querySelector('#scroll-progress');
    if (bar) {
      bar.style.transform = 'scaleX(' + ratio + ')';
      bar.classList.toggle('is-active', window.scrollY > 80 && maxScroll > 0);
    }
  };
  const updateScrollDirection = () => {
    const currentY = window.scrollY;
    if (Math.abs(currentY - previousScrollY) > 2) scrollDirection = currentY > previousScrollY ? 1 : -1;
    previousScrollY = currentY;
  };
  window.addEventListener('scroll', updateScrollDirection, { passive: true });
  const queueScrollChrome = () => {
    if (scrollFrame) return;
    scrollFrame = window.requestAnimationFrame(updateScrollChrome);
  };
  const progressBar = document.createElement('div');
  progressBar.id = 'scroll-progress';
  progressBar.setAttribute('aria-hidden', 'true');
  body.prepend(progressBar);
  window.addEventListener('scroll', queueScrollChrome, { passive: true });
  window.addEventListener('resize', queueScrollChrome, { passive: true });
  updateScrollChrome();

  const openMenu = () => {
    if (!menu || !menuToggle || menuIsOpen) return;
    previousFocus = document.activeElement;
    menuIsOpen = true;
    menu.inert = false;
    menu.setAttribute('aria-hidden', 'false');
    menu.classList.add('is-open');
    menuToggle.setAttribute('aria-expanded', 'true');
    menuToggle.setAttribute('aria-label', currentLanguage === 'en' ? 'Close navigation menu' : 'Đóng menu điều hướng');
    body.classList.add('menu-open');
    const first = menu.querySelector(menuFocusable);
    window.requestAnimationFrame(() => first?.focus());
  };
  const closeMenu = (restoreFocus = true) => {
    if (!menu || !menuToggle || !menuIsOpen) return;
    menuIsOpen = false;
    menu.classList.remove('is-open');
    menuToggle.setAttribute('aria-expanded', 'false');
    menuToggle.setAttribute('aria-label', currentLanguage === 'en' ? 'Open navigation menu' : 'Mở menu điều hướng');
    body.classList.remove('menu-open');
    window.setTimeout(() => {
      if (menuIsOpen) return;
      menu.inert = true;
      menu.setAttribute('aria-hidden', 'true');
    }, reducedMotion.matches ? 0 : 320);
    if (restoreFocus) menuToggle.focus();
    else if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus({ preventScroll: true });
  };
  menuToggle?.addEventListener('click', () => menuIsOpen ? closeMenu() : openMenu());
  menuClose?.addEventListener('click', () => closeMenu());
  menu?.querySelectorAll('a[href]').forEach((link) => link.addEventListener('click', () => closeMenu(false)));
  document.addEventListener('keydown', (event) => {
    if (menuIsOpen && event.key === 'Escape') {
      event.preventDefault();
      closeMenu();
      return;
    }
    if (menuIsOpen && event.key === 'Tab') {
      const items = [...menu.querySelectorAll(menuFocusable)].filter((item) => !item.hasAttribute('disabled'));
      if (!items.length) return;
      const first = items[0];
      const last = items[items.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
    if (dialog?.open && event.key === 'Escape') dialog.close();
  });
  window.addEventListener('resize', () => {
    if (window.innerWidth > 820 && menuIsOpen) closeMenu(false);
  }, { passive: true });

  const navigationLinks = [...document.querySelectorAll('.desktop-nav a, .mobile-menu-links a')];
  const navigationSections = ['features', 'demo', 'roadmap', 'faq'].map((id) => document.getElementById(id)).filter(Boolean);
  if ('IntersectionObserver' in window && navigationSections.length) {
    const navigationObserver = new IntersectionObserver((entries) => {
      const visibleSection = entries.filter((entry) => entry.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
      if (!visibleSection) return;
      navigationLinks.forEach((link) => {
        const active = link.getAttribute('href') === '#' + visibleSection.target.id;
        if (active) link.setAttribute('aria-current', 'location');
        else link.removeAttribute('aria-current');
      });
    }, { rootMargin: '-35% 0px -55% 0px', threshold: [0, 0.15, 0.3, 0.5] });
    navigationSections.forEach((section) => navigationObserver.observe(section));
  }

  const revealTargets = [...document.querySelectorAll('.section-intro, .feature-row, .demo-layout, .comparison-wrap, .roadmap-item, .setup-step, .faq-list, .download-panel')];
  if (!reducedMotion.matches && 'IntersectionObserver' in window) {
    const revealObserver = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        const target = entry.target;
        if (entry.isIntersecting) {
          target.style.setProperty('--reveal-y', scrollDirection > 0 ? '22px' : '-22px');
          target.classList.add('is-visible');
        } else {
          target.style.setProperty('--reveal-y', scrollDirection > 0 ? '-22px' : '22px');
          target.classList.remove('is-visible');
        }
      });
    }, { rootMargin: '0px 0px -6% 0px', threshold: 0.04 });
    revealTargets.forEach((target, index) => {
      target.classList.add('reveal');
      if (target.classList.contains('roadmap-item') || target.classList.contains('setup-step')) {
        target.style.setProperty('--reveal-delay', (index % 3) * 65 + 'ms');
      }
      revealObserver.observe(target);
    });
  } else {
    revealTargets.forEach((target) => target.classList.add('is-visible'));
  }

  const readingCopy = document.querySelector('[data-reading-copy]');
  let readingFrame = 0;
  const updateReadingHighlight = () => {
    readingFrame = 0;
    if (!readingCopy || reducedMotion.matches) return;
    const rect = readingCopy.getBoundingClientRect();
    const vh = window.innerHeight || 1;
    const progress = Math.min(1, Math.max(0, (vh * 0.82 - rect.top) / (rect.height + vh * 0.42)));
    readingCopy.style.setProperty('--reading-progress', (progress * 100).toFixed(2) + '%');
  };
  const queueReadingHighlight = () => {
    if (readingFrame) return;
    readingFrame = window.requestAnimationFrame(updateReadingHighlight);
  };
  window.addEventListener('scroll', queueReadingHighlight, { passive: true });
  window.addEventListener('resize', queueReadingHighlight, { passive: true });
  updateReadingHighlight();
  if (reducedMotion.matches && readingCopy) readingCopy.style.setProperty('--reading-progress', '100%');

  document.querySelectorAll('.tree-toggle').forEach((button) => {
    button.addEventListener('click', () => {
      const panel = document.getElementById(button.getAttribute('aria-controls'));
      if (!panel) return;
      const expanded = button.getAttribute('aria-expanded') === 'true';
      button.setAttribute('aria-expanded', String(!expanded));
      panel.classList.toggle('is-collapsed', expanded);
      panel.setAttribute('aria-hidden', String(expanded));
    });
  });
  document.querySelectorAll('.tree-file').forEach((button) => {
    button.addEventListener('click', () => {
      document.querySelectorAll('.tree-file').forEach((file) => file.classList.toggle('is-selected', file === button));
      const selectedName = document.querySelector('#selected-file-name');
      if (selectedName) selectedName.textContent = button.dataset.fileName || button.textContent.trim();
    });
  });

  const editorTabs = [...document.querySelectorAll('[data-editor-tab]')];
  const editorPanel = document.querySelector('#editor-tab-panel');
  const editorLanguage = document.querySelector('#editor-language');
  const editorLabels = { manifest: ['tab-manifest', 'JSON'], script: ['tab-script', 'JavaScript'], function: ['tab-function', 'MCFUNCTION'] };
  const activateEditorTab = (key, focus = false) => {
    const selected = editorTabs.find((tab) => tab.dataset.editorTab === key);
    if (!selected) return;
    editorTabs.forEach((tab) => {
      const active = tab === selected;
      tab.setAttribute('aria-selected', String(active));
      tab.tabIndex = active ? 0 : -1;
    });
    document.querySelectorAll('[data-code-sample]').forEach((sample) => {
      sample.hidden = sample.dataset.codeSample !== key;
    });
    if (editorPanel) editorPanel.setAttribute('aria-labelledby', editorLabels[key][0]);
    if (editorLanguage) editorLanguage.textContent = editorLabels[key][1];
    if (focus) selected.focus();
  };
  editorTabs.forEach((tab, index) => {
    tab.addEventListener('click', () => activateEditorTab(tab.dataset.editorTab));
    tab.addEventListener('keydown', (event) => {
      if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
      event.preventDefault();
      let next = index;
      if (event.key === 'ArrowLeft') next = (index - 1 + editorTabs.length) % editorTabs.length;
      if (event.key === 'ArrowRight') next = (index + 1) % editorTabs.length;
      if (event.key === 'Home') next = 0;
      if (event.key === 'End') next = editorTabs.length - 1;
      activateEditorTab(editorTabs[next].dataset.editorTab, true);
    });
  });

  const demoTabs = [...document.querySelectorAll('[data-demo-tab]')];
  const demoPanel = document.querySelector('[data-demo-panel]');
  const demoLabel = document.querySelector('#demo-screen-label');
  const demoLabels = { import: ['NHẬP PACK / TỆP', 'IMPORT / PACK FILE'], edit: ['SOẠN THẢO / TỆP VĂN BẢN', 'EDITOR / TEXT FILE'], manifest: ['MANIFEST / CHI TIẾT', 'MANIFEST / DETAILS'], export: ['XUẤT PACK', 'EXPORT / PACK'] };
  const activateDemoTab = (key, focus = false) => {
    const selected = demoTabs.find((tab) => tab.dataset.demoTab === key);
    if (!selected) return;
    demoTabs.forEach((tab) => {
      const active = tab === selected;
      tab.setAttribute('aria-selected', String(active));
      tab.tabIndex = active ? 0 : -1;
    });
    document.querySelectorAll('[data-demo-content]').forEach((panel) => {
      panel.hidden = panel.dataset.demoContent !== key;
    });
    if (demoPanel) demoPanel.setAttribute('aria-labelledby', selected.id);
    if (demoLabel) demoLabel.textContent = demoLabels[key] ? demoLabels[key][currentLanguage === 'en' ? 1 : 0] : 'BLOCKLINE / DEMO';
    const activeContent = document.querySelector('[data-demo-content="' + key + '"]');
    if (activeContent && !reducedMotion.matches) {
      activeContent.style.animation = 'none';
      void activeContent.offsetWidth;
      activeContent.style.animation = '';
    }
    if (focus) selected.focus();
  };
  demoTabs.forEach((tab, index) => {
    tab.addEventListener('click', () => activateDemoTab(tab.dataset.demoTab));
    tab.addEventListener('keydown', (event) => {
      if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
      event.preventDefault();
      let next = index;
      if (event.key === 'ArrowLeft') next = (index - 1 + demoTabs.length) % demoTabs.length;
      if (event.key === 'ArrowRight') next = (index + 1) % demoTabs.length;
      if (event.key === 'Home') next = 0;
      if (event.key === 'End') next = demoTabs.length - 1;
      activateDemoTab(demoTabs[next].dataset.demoTab, true);
    });
  });

  const faqItems = [...document.querySelectorAll('.faq-list details')];
  faqItems.forEach((item) => {
    item.addEventListener('toggle', () => {
      if (!item.open) return;
      faqItems.forEach((other) => { if (other !== item) other.open = false; });
    });
  });

  document.querySelectorAll('[data-current-year]').forEach((element) => {
    element.textContent = String(new Date().getFullYear());
  });
  let preferredLanguage = 'vi';
  try { preferredLanguage = localStorage.getItem('blockline-language') === 'en' ? 'en' : 'vi'; } catch (_) {}
  setLanguage(preferredLanguage);
})();