import { HttpContextToken, HttpInterceptorFn } from '@angular/common/http';

/**
 * Set on a `startLoginWith*` request to tell the BFF where to send the user back once the
 * authorization code flow completes (relayed as the `X-POST-LOGIN-SUCCESS-URI` header).
 */
export const POST_LOGIN_SUCCESS_URI = new HttpContextToken<string | undefined>(() => undefined);

/**
 * Set on a `logout` request to tell the BFF where to send the user back once RP-initiated logout
 * completes (relayed as the `X-POST-LOGOUT-SUCCESS-URI` header).
 */
export const POST_LOGOUT_SUCCESS_URI = new HttpContextToken<string | undefined>(() => undefined);

/**
 * The gateway is an OAuth2 BFF: the frontend never sees a token, requests are authorized with the
 * session cookie (hence `withCredentials`) and the CSRF cookie / header pair is handled by
 * Angular's XSRF support (see `app.config.ts`).
 *
 * Post-login / post-logout redirect URIs travel through the request context rather than as
 * explicit headers because the generated `@api/gateway` client does not expose request headers.
 */
export const bffInterceptor: HttpInterceptorFn = (req, next) => {
  const setHeaders: Record<string, string> = {};
  const postLoginSuccessUri = req.context.get(POST_LOGIN_SUCCESS_URI);
  if (postLoginSuccessUri) {
    setHeaders['X-POST-LOGIN-SUCCESS-URI'] = postLoginSuccessUri;
  }
  const postLogoutSuccessUri = req.context.get(POST_LOGOUT_SUCCESS_URI);
  if (postLogoutSuccessUri) {
    setHeaders['X-POST-LOGOUT-SUCCESS-URI'] = postLogoutSuccessUri;
  }
  return next(req.clone({ withCredentials: true, setHeaders }));
};
