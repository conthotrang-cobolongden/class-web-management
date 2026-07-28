package com.classmanagement.fees.dto;

// Ket qua sinh phi thang: created = so khoan tao moi, skipped = so khoan da ton tai (idempotent)
public record GenerateFeesResponse(int created, int skipped) {
}
