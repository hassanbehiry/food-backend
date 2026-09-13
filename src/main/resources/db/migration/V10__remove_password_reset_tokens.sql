-- The forgot/reset-password flow (POST /api/v1/auth/forgot-password, /reset-password) has been
-- removed, along with PasswordResetToken and its mailer. Drop the table V7 created for it.
DROP TABLE IF EXISTS public.password_reset_tokens;
