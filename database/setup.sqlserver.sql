-- ============================================================
-- Tao database + login cho app (chay MOT LAN bang tai khoan quan tri)
--   sqlcmd -S localhost\RYAN_SERVER -E -i database\setup.sqlserver.sql
-- Schema + seed KHONG chay o day — Flyway trong java-backend tu lo
-- khi app khoi dong lan dau.
-- Luu y: login SQL (class_app) can instance bat Mixed Mode authentication.
-- ============================================================

IF DB_ID('ClassManagement') IS NULL
    CREATE DATABASE ClassManagement;
GO

IF SUSER_ID('class_app') IS NULL
    CREATE LOGIN class_app
    WITH PASSWORD = 'ClassApp@2026!',
         DEFAULT_DATABASE = ClassManagement,
         CHECK_POLICY = OFF;   -- dev local; bat lai khi trien khai that
GO

USE ClassManagement;
GO

IF USER_ID('class_app') IS NULL
    CREATE USER class_app FOR LOGIN class_app;
GO

ALTER ROLE db_owner ADD MEMBER class_app;
GO

PRINT N'Xong: database ClassManagement + login class_app da san sang.';
