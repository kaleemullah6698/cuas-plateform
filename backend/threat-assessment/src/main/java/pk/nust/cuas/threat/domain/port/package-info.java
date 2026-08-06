/**
 * Outbound ports the domain declares and the infrastructure layer implements: persistence for
 * assessments and policies, and publication of what this service concluded. Interfaces only, and
 * framework-free — the JPA and Kafka adapters live under {@code infrastructure/}.
 */
package pk.nust.cuas.threat.domain.port;
