alter TABLE USERS
ADD COLUMN monzo_token varchar(300) NULL,
ADD COLUMN main_account_id varchar(200) NULL,
ADD COLUMN bilbo_pot_id varchar(200) NULL,
ADD COLUMN pot_deposit_day integer NULL;