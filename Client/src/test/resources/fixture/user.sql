insert into public.eve_assist_user (id, create_timestamp, update_timestamp, email, password, unique_user, screen_name,
                                    account_non_expired, account_non_locked, credentials_non_expired, enabled)
values (1, '2023-06-10 01:34:45.000000', '2023-06-10 01:34:45.000000', 'test@test.com', 'secret',
        '000000000000000000000000000001', 'test user', true, true, true, true),
       (2, '2023-06-10 01:34:45.000000', '2023-06-10 01:34:45.000000', 'test2@test.com', 'secret',
        '000000000000000000000000000002', 'test2 user', true, true, true, true);
