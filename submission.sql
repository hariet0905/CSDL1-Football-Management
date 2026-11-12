-- BAI NOP NHOM 26: khoi tao CSDL tu cac file theo chuc nang.
-- Chay bang mysql CLI tu thu muc goc project voi --abort-source-on-error.
-- Chi dung khi chua tao bang. Khong chay lai tren CSDL da co du lieu.
-- Workbench: mo va chay tung file db/01_schema.sql den db/05_reports.sql theo thu tu.
SOURCE db/01_schema.sql;
SOURCE db/02_triggers.sql;
SOURCE db/03_procedures.sql;
SOURCE db/04_seed.sql;
SOURCE db/05_reports.sql;
