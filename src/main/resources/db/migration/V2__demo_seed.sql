-- Demo ULBs from the meeting: Muni ki Reti, Pauri, Vikas Nagar, Mussoorie, Rudrapur

insert into ulb (code, name, district_code, district_name, ulb_type) values
    ('MKR', 'Muni ki Reti',  'TGH', 'Tehri Garhwal',     'NAGAR_PALIKA'),
    ('PRI', 'Pauri',         'PGH', 'Pauri Garhwal',     'NAGAR_PALIKA'),
    ('VKN', 'Vikas Nagar',   'DDN', 'Dehradun',          'NAGAR_PALIKA'),
    ('MSR', 'Mussoorie',     'DDN', 'Dehradun',          'NAGAR_PALIKA'),
    ('RDR', 'Rudrapur',      'USN', 'Udham Singh Nagar', 'NAGAR_NIGAM');

insert into ward (ulb_id, ward_no, name)
select u.id, w.ward_no, w.name
from ulb u
join (values
    ('MKR', 1, 'Swarg Ashram'), ('MKR', 2, 'Kailash Gate'), ('MKR', 3, 'Shishambada'),
    ('PRI', 1, 'Agency Mohalla'), ('PRI', 2, 'Chopra'), ('PRI', 3, 'Kandoliya'),
    ('VKN', 1, 'Main Bazaar'), ('VKN', 2, 'Kalsi Gate'), ('VKN', 3, 'Herbertpur Road'),
    ('MSR', 1, 'Landour'), ('MSR', 2, 'Kulri'), ('MSR', 3, 'Library Chowk'),
    ('RDR', 1, 'Awas Vikas'), ('RDR', 2, 'Transit Camp'), ('RDR', 3, 'Kichha Road')
) as w(ulb_code, ward_no, name) on w.ulb_code = u.code;

insert into ulb_tax_config (ulb_id, res_carpet_rate, nonres_carpet_rate, nonres_coefficient, nonres_multiplier,
                            property_multiplier, arv_tax_rate, vacant_land_rate, circle_rate, general_tax_rate,
                            building_depreciation, mutation_fee, mutation_payment_required, rules_effective_from)
select u.id, c.res_rate, c.nonres_rate, c.coeff, c.nonres_mult, c.prop_mult, c.tax_rate, c.vacant_rate,
       c.circle_rate, c.gen_rate, c.depr, c.fee, c.pay_req, date '2024-04-01'
from ulb u
join (values
    ('MKR', 2.00, 5.00, 1.50, 1.00, 1.00, 10.0, 0.50, 2800.00, 0.100, 0.800, 500.00, false),
    ('PRI', 1.50, 4.00, 1.25, 1.00, 1.00,  8.0, 0.40, 1800.00, 0.100, 0.800, 300.00, true),
    ('VKN', 1.75, 4.50, 1.50, 1.00, 1.10,  8.0, 0.45, 2200.00, 0.100, 0.750, 500.00, true),
    ('MSR', 3.00, 8.00, 2.00, 1.20, 1.20, 12.0, 0.90, 6500.00, 0.080, 0.800, 1000.00, true),
    ('RDR', 2.25, 6.00, 1.75, 1.00, 1.00, 10.0, 0.60, 3200.00, 0.100, 0.750, 750.00, true)
) as c(ulb_code, res_rate, nonres_rate, coeff, nonres_mult, prop_mult, tax_rate, vacant_rate, circle_rate, gen_rate, depr, fee, pay_req)
  on c.ulb_code = u.code;

-- Standard chain everywhere, except Pauri which skips the verifier (to show it's configurable)
insert into ulb_workflow_step (ulb_id, step_order, role)
select u.id, s.step_order, s.role
from ulb u
cross join (values
    (1, 'RECORD_KEEPER'), (2, 'CLERK'), (3, 'VERIFIER'), (4, 'FIELD_INSPECTOR'), (5, 'APPROVER')
) as s(step_order, role)
where u.code <> 'PRI';

insert into ulb_workflow_step (ulb_id, step_order, role)
select u.id, s.step_order, s.role
from ulb u
cross join (values
    (1, 'RECORD_KEEPER'), (2, 'CLERK'), (3, 'FIELD_INSPECTOR'), (4, 'APPROVER')
) as s(step_order, role)
where u.code = 'PRI';

-- Users. Passwords are filled in on startup by DemoPasswordInitializer (demo123)

insert into app_user (username, full_name, role, ulb_id, mobile, email)
values ('admin', 'State Admin (UDD)', 'ADMIN', null, '9412000000', 'admin@udd.uk.gov.in');

insert into app_user (username, full_name, role, ulb_id, mobile, email)
select lower(r.short) || '.' || lower(u.code),
       r.title || ' - ' || u.name,
       r.role,
       u.id,
       '94120' || lpad((u.id * 10 + r.n)::text, 5, '0'),
       lower(r.short) || '.' || lower(u.code) || '@ulb.uk.gov.in'
from ulb u
cross join (values
    (1, 'RK',        'RECORD_KEEPER',   'Record Keeper'),
    (2, 'CLERK',     'CLERK',           'Clerk'),
    (3, 'VERIFIER',  'VERIFIER',        'Verifier'),
    (4, 'INSPECTOR', 'FIELD_INSPECTOR', 'Field Inspector'),
    (5, 'APPROVER',  'APPROVER',        'Executive Officer'),
    (6, 'COUNTER',   'COUNTER',         'Counter Operator')
) as r(n, short, role, title);

-- second clerk and a ward-restricted record keeper in Vikas Nagar
insert into app_user (username, full_name, role, ulb_id, mobile, email)
select 'clerk2.vkn', 'Clerk 2 - Vikas Nagar', 'CLERK', id, '9412099902', 'clerk2.vkn@ulb.uk.gov.in' from ulb where code = 'VKN';
insert into app_user (username, full_name, role, ulb_id, mobile, email)
select 'rk2.vkn', 'Record Keeper 2 - Vikas Nagar', 'RECORD_KEEPER', id, '9412099903', 'rk2.vkn@ulb.uk.gov.in' from ulb where code = 'VKN';

insert into app_user (username, full_name, role, ulb_id, mobile, email) values
    ('ramesh',  'Ramesh Chand Negi', 'CITIZEN', null, '9876500011', 'ramesh.negi@example.com'),
    ('sunita',  'Sunita Rawat',      'CITIZEN', null, '9876500012', 'sunita.rawat@example.com');

-- record keepers see all wards of their ULB, rk2.vkn only ward 3
insert into user_ward (user_id, ward_id)
select au.id, w.id
from app_user au
join ward w on w.ulb_id = au.ulb_id
where au.role = 'RECORD_KEEPER' and au.username <> 'rk2.vkn';

insert into user_ward (user_id, ward_id)
select au.id, w.id
from app_user au
join ward w on w.ulb_id = au.ulb_id and w.ward_no = 3
where au.username = 'rk2.vkn';

-- Properties

insert into property (property_no, ulb_id, ward_id, serial_no, khasra_no, category, owner_name, owner_mobile,
                      owner_email, address, plot_area, covered_area, carpet_area, construction_year, last_mutation_date)
select 'UK-' || u.district_code || '-' || u.code || '-' || lpad(p.ward_no::text, 2, '0') || '-' || lpad(p.serial_no::text, 5, '0'),
       u.id, w.id, p.serial_no, p.khasra, p.category, p.owner, p.mobile, p.email, p.address,
       p.plot, p.covered, p.carpet, p.built, p.last_mut
from (values
    ('VKN', 1, 1, '112/3',  'RESIDENTIAL',     'Mohan Lal Bhatt',     '9876500101', null,                       'H.No. 14, Main Bazaar',            1800, 1200, 1000, 2004, date '2019-06-11'),
    ('VKN', 1, 2, '112/7',  'NON_RESIDENTIAL', 'Bhatt General Store', '9876500102', 'bhattstore@example.com',   'Shop 3, Main Bazaar',               600,  550,  480, 1998, null),
    ('VKN', 1, 3, '118',    'RESIDENTIAL',     'Sunita Rawat',        '9876500012', 'sunita.rawat@example.com', 'H.No. 22, Gandhi Road',            2400, 1500, 1250, 2012, date '2021-02-02'),
    ('VKN', 2, 1, '204/1',  'RESIDENTIAL',     'Gurpreet Singh',      '9876500104', null,                       'Near Kalsi Gate, Lane 2',          1500, 1100,  900, 2009, null),
    ('VKN', 2, 2, '207',    'VACANT_LAND',     'Kamla Devi',          '9876500105', null,                       'Plot 7, Kalsi Gate Extension',     3000,    0,    0, null, null),
    ('VKN', 3, 1, '310/2',  'NON_RESIDENTIAL', 'Doon Valley Hotel',   '9876500106', 'dvhotel@example.com',      'Herbertpur Road, Km 2',            5000, 4200, 3600, 2015, date '2016-08-19'),
    ('VKN', 3, 2, '311',    'RESIDENTIAL',     'Rajesh Kumar Thapa',  '9876500107', null,                       'H.No. 5, Herbertpur Road',         1200,  900,  750, 1995, null),
    ('MSR', 1, 1, '45',     'RESIDENTIAL',     'Ruskin Villa Trust',  '9876500201', null,                       'Ivy Cottage, Landour',             4000, 2600, 2200, 1910, null),
    ('MSR', 1, 2, '47/1',   'NON_RESIDENTIAL', 'Char Dukan Cafe',     '9876500202', 'chardukan@example.com',    'Char Dukan, Landour',               800,  700,  600, 1960, date '2018-03-15'),
    ('MSR', 2, 1, '88',     'NON_RESIDENTIAL', 'Kulri Emporium',      '9876500203', null,                       'Mall Road, Kulri',                 1000,  950,  800, 1985, null),
    ('MSR', 3, 1, '130',    'RESIDENTIAL',     'Anita Joshi',         '9876500204', 'anita.j@example.com',      'Camel Back Road',                  2000, 1400, 1150, 2001, date '2020-11-30'),
    ('MKR', 1, 1, '21',     'NON_RESIDENTIAL', 'Ganga Ashram Trust',  '9876500301', null,                       'Swarg Ashram, near Ram Jhula',     6000, 4800, 4000, 1975, null),
    ('MKR', 2, 1, '56',     'RESIDENTIAL',     'Devendra Uniyal',     '9876500302', null,                       'Kailash Gate',                     1400, 1000,  850, 2010, null),
    ('PRI', 1, 1, '9',      'RESIDENTIAL',     'Harish Chandra Bisht','9876500401', null,                       'Agency Mohalla',                   1600, 1000,  800, 1990, null),
    ('PRI', 2, 1, '33',     'VACANT_LAND',     'Pushpa Negi',         '9876500402', null,                       'Chopra, near water tank',          2500,    0,    0, null, null),
    ('RDR', 1, 1, '501',    'RESIDENTIAL',     'Sukhwinder Kaur',     '9876500501', null,                       'Awas Vikas, Sector 2',             2000, 1600, 1300, 2014, date '2022-01-10'),
    ('RDR', 2, 1, '540/2',  'NON_RESIDENTIAL', 'Kumaon Agro Traders', '9876500502', 'kumaonagro@example.com',   'Transit Camp Market',              3000, 2800, 2400, 2006, null)
) as p(ulb_code, ward_no, serial_no, khasra, category, owner, mobile, email, address, plot, covered, carpet, built, last_mut)
join ulb u on u.code = p.ulb_code
join ward w on w.ulb_id = u.id and w.ward_no = p.ward_no;

-- Last year's assessment (self-assessment ARV method) so this year's assessment has something to compare to

insert into tax_assessment (property_id, financial_year, method, annual_value, computed_tax, final_tax, breakdown, assessed_at)
select p.id, '2025-26', 'SELF_ASSESSMENT', x.arv, round(x.arv * c.arv_tax_rate / 100, 2), round(x.arv * c.arv_tax_rate / 100, 2),
       'Migrated from legacy register', timestamp '2025-05-15 11:00:00'
from property p
join ulb_tax_config c on c.ulb_id = p.ulb_id
cross join lateral (
    select case p.category
        when 'RESIDENTIAL'     then round(p.carpet_area * c.res_carpet_rate * 12 * c.property_multiplier, 2)
        when 'NON_RESIDENTIAL' then round(p.covered_area * c.nonres_carpet_rate * c.nonres_coefficient * 12 * c.nonres_multiplier, 2)
        else round(p.plot_area * c.vacant_land_rate * 12, 2)
    end as arv
) x;

-- Demands for 2025-26. Some fully paid, some partly, some not at all
insert into demand (property_id, demand_type, financial_year, amount, paid_amount, created_at)
select a.property_id, 'PROPERTY_TAX', '2025-26', a.final_tax,
       case
           when p.owner_name in ('Rajesh Kumar Thapa', 'Kulri Emporium', 'Gurpreet Singh') then 0
           when p.owner_name in ('Bhatt General Store', 'Kamla Devi', 'Pushpa Negi') then round(a.final_tax / 2, 0)
           else a.final_tax
       end,
       timestamp '2025-05-15 11:00:00'
from tax_assessment a
join property p on p.id = a.property_id;

insert into payment (demand_id, receipt_no, amount, mode, paid_at)
select d.id, 'RCPT-25-' || lpad(d.id::text, 6, '0'), d.paid_amount, 'CASH', timestamp '2025-07-01 12:30:00'
from demand d
where d.paid_amount > 0;

-- A couple of mutation applications already in flight in Vikas Nagar

insert into mutation_application (application_no, property_id, ulb_id, reason, previous_owner, new_owner_name, new_owner_mobile,
                                  new_owner_email, deed_no, deed_date, status, current_step, pending_role, disputed,
                                  filed_via, filed_by, sla_due_date, created_at)
select 'MUT-VKN-2026-0001', p.id, p.ulb_id, 'SALE', p.owner_name, 'Ramesh Chand Negi', '9876500011',
       'ramesh.negi@example.com', 'DDN/VKN/2026/1187', date '2026-08-02', 'IN_PROGRESS', 2, 'CLERK', false,
       'CITIZEN', (select id from app_user where username = 'ramesh'),
       (current_date - 70) + 60, current_date - 70
from property p where p.owner_name = 'Gurpreet Singh';

insert into mutation_application (application_no, property_id, ulb_id, reason, previous_owner, new_owner_name, new_owner_mobile,
                                  new_owner_email, deed_no, deed_date, status, current_step, pending_role, disputed,
                                  filed_via, filed_by, sla_due_date, created_at)
select 'MUT-VKN-2026-0002', p.id, p.ulb_id, 'INHERITANCE', p.owner_name, 'Pooja Bhatt', '9876500111',
       null, null, null, 'IN_PROGRESS', 1, 'RECORD_KEEPER', false,
       'COUNTER', (select id from app_user where username = 'counter.vkn'),
       (current_date - 12) + 60, current_date - 12
from property p where p.owner_name = 'Mohan Lal Bhatt';

insert into mutation_action (mutation_id, step_role, action, remarks, action_by, action_at)
select m.id, null, 'SUBMITTED',
       case when m.filed_via = 'COUNTER' then 'Filed at counter' else 'Filed online' end,
       m.filed_by, m.created_at
from mutation_application m;

insert into mutation_action (mutation_id, step_role, action, remarks, action_by, action_at)
select m.id, 'RECORD_KEEPER', 'FORWARDED', 'Record found in ward register, khasra matches', u.id, m.created_at + interval '3 days'
from mutation_application m
join app_user u on u.username = 'rk.vkn'
where m.application_no = 'MUT-VKN-2026-0001';

insert into demand (property_id, demand_type, mutation_id, amount, paid_amount, created_at)
select m.property_id, 'MUTATION_FEE', m.id, c.mutation_fee, 0, m.created_at
from mutation_application m
join ulb_tax_config c on c.ulb_id = m.ulb_id;
