/**
 * Command-line entry point: one sub-command per action, with the output flags shared by all of them.
 * <p>
 * Every action is a separate Aesh command because Aesh resolves them without reflection, which keeps startup cheap
 * and gives each action its own help and tab completion. Commands only parse input, check that the configuration is
 * complete, and report results; the work happens in the business components.
 * <p>
 * {@code --config} is read before Quarkus starts, because the configuration file has to be known when the
 * configuration is built.
 */
package bogdanpc.linearsync.cli;
