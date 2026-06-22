USE student_dormitory;

INSERT INTO students (student_id, name, department, class_name, dorm_number, dorm_phone, bed_number)
VALUES
  ('20230001', '张明', '计算机系', '软件2301', '3-501', '0571-3501', '1'),
  ('20230002', '李华', '计算机系', '软件2301', '3-501', '0571-3501', '2'),
  ('20230003', '王芳', '信息工程系', '物联2302', '3-502', '0571-3502', '1'),
  ('20230004', '赵强', '机电工程系', '机电2301', '2-401', '0571-2401', '3'),
  ('20230005', '陈晨', '外语系', '英语2301', '2-402', '0571-2402', '2'),
  ('20230006', '刘洋', '计算机系', '网络2301', '3-502', '0571-3502', '2'),
  ('20230007', '孙悦', '计算机系', '软件2302', '4-301', '0571-4301', '1'),
  ('20230008', '周杰', '计算机系', '软件2302', '4-301', '0571-4301', '2'),
  ('20230009', '吴桐', '计算机系', '网络2302', '4-301', '0571-4301', '3'),
  ('20230010', '郑琳', '信息工程系', '物联2301', '4-302', '0571-4302', '1'),
  ('20230011', '冯凯', '信息工程系', '大数据2301', '4-302', '0571-4302', '2'),
  ('20230012', '陈雨', '信息工程系', '大数据2301', '4-302', '0571-4302', '3'),
  ('20230013', '蒋欣', '经济管理系', '会计2301', '4-303', '0571-4303', '1'),
  ('20230014', '何晨', '经济管理系', '电商2301', '4-303', '0571-4303', '2'),
  ('20230015', '马宁', '外语系', '英语2302', '4-303', '0571-4303', '3'),
  ('20230016', '朱磊', '机电工程系', '机电2302', '5-201', '0571-5201', '1'),
  ('20230017', '高洁', '机电工程系', '智能制造2301', '5-201', '0571-5201', '2'),
  ('20230018', '林浩', '艺术设计系', '视觉2301', '5-202', '0571-5202', '1'),
  ('20230019', '郭敏', '艺术设计系', '环艺2301', '5-202', '0571-5202', '2'),
  ('20230020', '宋佳', '经济管理系', '电商2302', '2-403', '0571-2403', '1'),
  ('20230021', '唐宇', '计算机系', '软件2303', '3-503', '0571-3503', '1'),
  ('20230022', '许诺', '计算机系', '软件2303', '3-503', '0571-3503', '2'),
  ('20230023', '沈琪', '信息工程系', '物联2303', '3-504', '0571-3504', '1'),
  ('20230024', '邓超', '外语系', '商务英语2301', '2-404', '0571-2404', '1')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  department = VALUES(department),
  class_name = VALUES(class_name),
  dorm_number = VALUES(dorm_number),
  dorm_phone = VALUES(dorm_phone),
  bed_number = VALUES(bed_number);

INSERT INTO change_requests (
  id, student_id, current_dorm_number, current_bed_number,
  target_dorm_number, target_dorm_phone, target_bed_number,
  reason, status, created_at, handled_at, admin_comment
)
VALUES
  ('REQ-DEMO-001', '20230008', '4-301', '2', '4-304', '0571-4304', '1',
   '希望调到靠近同班同学的宿舍，便于课程项目协作。', 'PENDING', NOW(), NULL, ''),
  ('REQ-DEMO-002', '20230018', '5-202', '1', '5-203', '0571-5203', '2',
   '原宿舍距离设计实训室较远，希望调换后方便晚间实训。', 'REJECTED', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), '目标床位暂不开放。'),
  ('REQ-DEMO-003', '20230020', '2-403', '1', '2-405', '0571-2405', '1',
   '因社团工作需要，希望调到同楼层宿舍。', 'APPROVED', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), '已完成线下协调。')
ON DUPLICATE KEY UPDATE
  student_id = VALUES(student_id),
  current_dorm_number = VALUES(current_dorm_number),
  current_bed_number = VALUES(current_bed_number),
  target_dorm_number = VALUES(target_dorm_number),
  target_dorm_phone = VALUES(target_dorm_phone),
  target_bed_number = VALUES(target_bed_number),
  reason = VALUES(reason),
  status = VALUES(status),
  admin_comment = VALUES(admin_comment);
