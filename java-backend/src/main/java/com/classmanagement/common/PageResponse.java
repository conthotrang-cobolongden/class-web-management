package com.classmanagement.common;

import java.util.List;

// Bao danh sach phan trang: {items, total, page, page_size} — giu contract voi ban Node (NFR-6)
public record PageResponse<T>(List<T> items, long total, int page, int pageSize) {
}
