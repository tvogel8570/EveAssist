insert into public.eve_assist_user (id, create_timestamp, update_timestamp, email, password, unique_user, screen_name,
                                    account_non_expired, account_non_locked, credentials_non_expired, enabled)
values (1, '2023-06-10 01:34:45.000000', '2023-06-10 01:34:45.000000', 'test@test.com', 'secret',
        'dd6f91d9-d931-461b-ac16-8b93ed9f0f77', 'test user', true, true, true, true),
       (2, '2023-06-10 01:34:45.000000', '2023-06-10 01:34:45.000000', 'test2@test.com', 'secret',
        'b906a5f4-87dc-45c5-8a34-009cb9314a1e', 'test2 user', true, true, true, true);
