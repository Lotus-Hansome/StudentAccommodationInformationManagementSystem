CREATE DATABASE IF NOT EXISTS student_dormitory
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE student_dormitory;

CREATE TABLE IF NOT EXISTS buildings (
  building_number VARCHAR(32) PRIMARY KEY,
  building_name VARCHAR(100) NOT NULL,
  gender_type VARCHAR(32) NOT NULL DEFAULT 'MIXED',
  total_floors INT NOT NULL DEFAULT 6,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dorm_rooms (
  dorm_number VARCHAR(32) PRIMARY KEY,
  building_number VARCHAR(32) NOT NULL,
  floor_number INT NOT NULL,
  room_type VARCHAR(64) NOT NULL DEFAULT '标准四人间',
  gender_type VARCHAR(32) NOT NULL DEFAULT 'MIXED',
  capacity INT NOT NULL DEFAULT 4,
  phone VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_room_building (building_number),
  KEY idx_room_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS beds (
  dorm_number VARCHAR(32) NOT NULL,
  bed_number VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (dorm_number, bed_number),
  KEY idx_bed_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

CREATE TABLE IF NOT EXISTS users (
  username VARCHAR(64) PRIMARY KEY,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(32) NOT NULL,
  student_id VARCHAR(32) NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  last_login_at DATETIME NULL,
  KEY idx_user_role (role),
  KEY idx_user_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS operation_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  operator VARCHAR(64) NOT NULL,
  action VARCHAR(64) NOT NULL,
  target_type VARCHAR(64) NOT NULL,
  target_id VARCHAR(128) NOT NULL,
  detail VARCHAR(1000) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  KEY idx_log_created (created_at),
  KEY idx_log_operator (operator)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
