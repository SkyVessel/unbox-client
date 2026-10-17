package dev.unbox.client;
/** Auth boundary: no credentials, sockets, or actual game session in this headless harness. */
public final class SocialLogin { public static boolean trusted=true; public static boolean trustedClientSession(){return trusted;} }
