-- Running numbers for receipts / applications / certificates
create sequence receipt_seq start 1001;
create sequence mutation_no_seq start 3;  -- 0001 and 0002 are used by seed data
create sequence certificate_seq start 1;

-- Core masters

create table ulb (
    id              bigserial primary key,
    code            varchar(10)  not null unique,   -- used in property id, e.g. VKN
    name            varchar(120) not null,
    district_code   varchar(10)  not null,
    district_name   varchar(120) not null,
    ulb_type        varchar(30)  not null            -- NAGAR_NIGAM / NAGAR_PALIKA / NAGAR_PANCHAYAT
);

create table ward (
    id       bigserial primary key,
    ulb_id   bigint not null references ulb(id),
    ward_no  int    not null,
    name     varchar(120) not null,
    unique (ulb_id, ward_no)
);

-- Every rate in the tax formula is decided by the ULB, so it all lives here
create table ulb_tax_config (
    ulb_id                     bigint primary key references ulb(id),
    res_carpet_rate            numeric(10,2) not null,  -- Rs / sq ft / month
    nonres_carpet_rate         numeric(10,2) not null,
    nonres_coefficient         numeric(6,3)  not null,
    nonres_multiplier          numeric(6,3)  not null,
    property_multiplier        numeric(6,3)  not null,
    arv_tax_rate               numeric(6,3)  not null,  -- percent
    vacant_land_rate           numeric(10,2) not null,  -- Rs / sq ft / month
    circle_rate                numeric(12,2) not null,  -- Rs / sq ft
    general_tax_rate           numeric(6,3)  not null,  -- percent
    building_depreciation      numeric(6,3)  not null,  -- factor applied on building value (residential)
    mutation_fee               numeric(10,2) not null,
    mutation_payment_required  boolean       not null default true,
    rules_effective_from       date          not null
);

-- Mutation approval chain, configurable per ULB
create table ulb_workflow_step (
    id          bigserial primary key,
    ulb_id      bigint      not null references ulb(id),
    step_order  int         not null,
    role        varchar(30) not null,
    unique (ulb_id, step_order)
);

create table app_user (
    id             bigserial primary key,
    username       varchar(60)  not null unique,
    password_hash  varchar(100),
    full_name      varchar(120) not null,
    role           varchar(30)  not null,
    ulb_id         bigint references ulb(id),   -- null for state admin / citizens
    mobile         varchar(15),
    email          varchar(120),
    active         boolean not null default true
);

create table user_ward (
    user_id  bigint not null references app_user(id),
    ward_id  bigint not null references ward(id),
    primary key (user_id, ward_id)
);

-- Property register

create table property (
    id                  bigserial primary key,
    property_no         varchar(40)  not null unique,  -- state-district-ulb-ward-serial
    ulb_id              bigint       not null references ulb(id),
    ward_id             bigint       not null references ward(id),
    serial_no           int          not null,
    khasra_no           varchar(40),
    category            varchar(20)  not null,   -- RESIDENTIAL / NON_RESIDENTIAL / VACANT_LAND
    owner_name          varchar(120) not null,
    owner_mobile        varchar(15),
    owner_email         varchar(120),
    address             varchar(255) not null,
    plot_area           numeric(12,2) not null default 0,  -- sq ft
    covered_area        numeric(12,2) not null default 0,
    carpet_area         numeric(12,2) not null default 0,
    construction_year   int,
    last_mutation_date  date,
    created_at          timestamp not null default now(),
    unique (ward_id, serial_no)
);

create index idx_property_ulb_ward on property(ulb_id, ward_id);

create table tax_assessment (
    id               bigserial primary key,
    property_id      bigint       not null references property(id),
    financial_year   varchar(7)   not null,   -- 2026-27
    method           varchar(30)  not null,
    annual_value     numeric(14,2) not null,
    computed_tax     numeric(12,2) not null,
    final_tax        numeric(12,2) not null,
    breakdown        text,
    assessed_by      bigint references app_user(id),
    assessed_at      timestamp not null default now(),
    unique (property_id, financial_year)
);

create table demand (
    id              bigserial primary key,
    property_id     bigint       not null references property(id),
    demand_type     varchar(20)  not null,   -- PROPERTY_TAX / MUTATION_FEE
    financial_year  varchar(7),
    mutation_id     bigint,
    amount          numeric(12,2) not null,
    paid_amount     numeric(12,2) not null default 0,
    created_at      timestamp not null default now()
);

create index idx_demand_property on demand(property_id);

create table payment (
    id            bigserial primary key,
    demand_id     bigint       not null references demand(id),
    receipt_no    varchar(30)  not null unique,
    amount        numeric(12,2) not null,
    mode          varchar(20)  not null,   -- CASH / UPI / CARD / NETBANKING
    collected_by  bigint references app_user(id),
    paid_at       timestamp not null default now()
);

-- Mutation

create table mutation_application (
    id                 bigserial primary key,
    application_no     varchar(30)  not null unique,
    property_id        bigint       not null references property(id),
    ulb_id             bigint       not null references ulb(id),
    reason             varchar(20)  not null,   -- SALE / INHERITANCE / GIFT / COURT_ORDER
    previous_owner     varchar(120) not null,
    new_owner_name     varchar(120) not null,
    new_owner_mobile   varchar(15)  not null,
    new_owner_email    varchar(120),
    deed_no            varchar(60),
    deed_date          date,
    status             varchar(20)  not null,   -- IN_PROGRESS / APPROVED / REJECTED
    current_step       int,
    pending_role       varchar(30),
    disputed           boolean not null default false,
    filed_via          varchar(20)  not null,   -- CITIZEN / COUNTER
    filed_by           bigint references app_user(id),
    sla_due_date       date not null,
    created_at         timestamp not null default now(),
    closed_at          timestamp
);

create index idx_mutation_queue on mutation_application(ulb_id, status, pending_role);

alter table demand add constraint fk_demand_mutation
    foreign key (mutation_id) references mutation_application(id);

create table mutation_action (
    id           bigserial primary key,
    mutation_id  bigint      not null references mutation_application(id),
    step_role    varchar(30),
    action       varchar(20) not null,
    remarks      varchar(500),
    action_by    bigint references app_user(id),
    action_at    timestamp not null default now()
);

create table no_dues_certificate (
    id              bigserial primary key,
    certificate_no  varchar(30) not null unique,
    property_id     bigint      not null references property(id),
    mutation_id     bigint references mutation_application(id),
    issued_at       timestamp not null default now(),
    valid_till      date not null
);

-- We don't have an SMS gateway in the demo, so everything that would be sent lands here
create table notification (
    id         bigserial primary key,
    channel    varchar(10)  not null,  -- SMS / EMAIL
    recipient  varchar(120) not null,
    message    varchar(1000) not null,
    ref_type   varchar(30),
    ref_id     bigint,
    ulb_id     bigint references ulb(id),
    sent_at    timestamp not null default now()
);
