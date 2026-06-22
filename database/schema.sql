CREATE DATABASE IF NOT EXISTS student_dormitory
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE student_dormitory;

CREATE TABLE IF NOT EXISTS students (
  student_id VARCHAR(32) PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  department VARCHAR(100) NOT NULL,
  class_name VARCHAR(100) NOT NULL,
  dorm_number VARCHAR(32) NOT NULL,
  dorm_phone VARCHAR(32) NOT NULL,
  bed_number VARCHAR(32) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_dorm_bed (dorm_number, bed_number),
  KEY idx_dorm_number (dorm_number),
  KEY idx_department_class (department, class_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS change_requests (
  id VARCHAR(64) PRIMARY KEY,
  student_id VARCHAR(32) NOT NULL,
  current_dorm_number VARCHAR(32) NOT NULL,
  current_bed_number VARCHAR(32) NOT NULL,
  target_dorm_number VARCHAR(32) NOT NULL,
  target_dorm_phone VARCHAR(32) NOT NULL,
  target_bed_number VARCHAR(32) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL,
  handled_at DATETIME NULL,
  admin_comment VARCHAR(500) NULL,
  KEY idx_request_status (status),
  KEY idx_request_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
