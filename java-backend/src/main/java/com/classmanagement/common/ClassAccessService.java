package com.classmanagement.common;

import com.classmanagement.domain.ClassStaff;
import com.classmanagement.domain.ClassStaffRepository;
import com.classmanagement.user.User;
import org.springframework.stereotype.Service;

import java.util.List;

// Phan quyen theo lop (NFR-2) — port tu backend/src/middleware/classAccess.js.
// member = admin HOAC duoc gan vao lop; manager = admin HOAC GV chinh cua lop.
@Service
public class ClassAccessService {

    public static final String MEMBER_DENIED = "Bạn không được gán vào lớp này";
    public static final String MANAGER_DENIED = "Chỉ Admin hoặc giáo viên chính của lớp được thực hiện thao tác này";

    private final ClassStaffRepository classStaff;

    public ClassAccessService(ClassStaffRepository classStaff) {
        this.classStaff = classStaff;
    }

    public static boolean isAdmin(User user) {
        return "admin".equals(user.getRole());
    }

    // "admin" | "main" | "assistant" | null (khong lien quan gi den lop)
    public String classRole(User user, Long classId) {
        if (isAdmin(user)) {
            return "admin";
        }
        return classStaff.findByClassIdAndUserId(classId, user.getId())
                .map(ClassStaff::getStaffRole)
                .orElse(null);
    }

    // Chan 403 neu khong phai member cua lop (admin luon qua)
    public void assertClassMember(User user, Long classId) {
        if (classRole(user, classId) == null) {
            throw new ApiException(403, MEMBER_DENIED);
        }
    }

    // Chan 403 neu khong phai admin/GV chinh cua lop
    public void assertClassManager(User user, Long classId) {
        String role = classRole(user, classId);
        if (!"admin".equals(role) && !"main".equals(role)) {
            throw new ApiException(403, MANAGER_DENIED);
        }
    }

    // Cac lop user nhin thay: admin -> null (nghia la TAT CA, khong loc); GV/TG -> id lop duoc gan
    public List<Long> visibleClassIds(User user) {
        if (isAdmin(user)) {
            return null;
        }
        return classStaff.findByUserId(user.getId()).stream()
                .map(ClassStaff::getClassId)
                .distinct()
                .toList();
    }
}
