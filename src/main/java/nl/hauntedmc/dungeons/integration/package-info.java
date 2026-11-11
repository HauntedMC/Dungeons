/**
 * External player identity, language-preference and privacy capabilities, plus the public API
 * adapter.
 *
 * <p>Message storage and delivery belong to {@code localization}; database durability belongs to
 * {@code persistence}. External capability failures are handled at this boundary.
 */
package nl.hauntedmc.dungeons.integration;
