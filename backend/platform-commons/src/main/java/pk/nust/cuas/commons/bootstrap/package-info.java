/**
 * First-administrator bootstrap, shared by auth-service and user-service.
 *
 * <p>The two services own separate databases and each seeds its own half — the credential and the
 * identity. They agree by both binding the same {@link
 * pk.nust.cuas.commons.bootstrap.BootstrapAdminProperties}, which is why it lives here rather than
 * being declared twice.
 */
package pk.nust.cuas.commons.bootstrap;
