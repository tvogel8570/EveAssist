truncate TABLE eve_assist_privilege cascade;
truncate table eve_assist_role cascade;
truncate table roles_privileges cascade;
truncate table users_roles cascade;
INSERT INTO public.eve_assist_role (id, role_name) VALUES(0, 'ADMIN');
INSERT INTO public.eve_assist_role (id, role_name) VALUES(1, 'MANAGER');
INSERT INTO public.eve_assist_role (id, role_name) VALUES(2, 'USER');
ALTER SEQUENCE eve_assist_role_id_seq START 3 INCREMENT 1;
INSERT INTO public.eve_assist_privilege (id, priv_name) VALUES(0, 'READ');
INSERT INTO public.eve_assist_privilege (id, priv_name) VALUES(1, 'UPDATE');
INSERT INTO public.eve_assist_privilege (id, priv_name) VALUES(2, 'CREATE');
INSERT INTO public.eve_assist_privilege (id, priv_name) VALUES(3, 'DELETE');
ALTER SEQUENCE eve_assist_priv_id_seq START 4 INCREMENT 1;
INSERT INTO public.roles_privileges (role_id, privilege_id) VALUES(0, 0);	-- ADMIN READ
INSERT INTO public.roles_privileges (role_id, privilege_id) VALUES(0, 1);	-- ADMIN UPDATE 
INSERT INTO public.roles_privileges (role_id, privilege_id) VALUES(0, 2);	-- ADMIN CREATE
INSERT INTO public.roles_privileges (role_id, privilege_id) VALUES(0, 3);	-- ADMIN DELETE
INSERT INTO public.roles_privileges (role_id, privilege_id) VALUES(1, 0);	-- MANAGER READ
INSERT INTO public.roles_privileges (role_id, privilege_id) VALUES(1, 1);	-- MANAGER UPDATE
INSERT INTO public.roles_privileges (role_id, privilege_id) VALUES(2, 0);	-- USER READ
