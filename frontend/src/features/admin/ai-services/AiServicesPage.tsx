import { BrainCircuit } from 'lucide-react';
import { Badge, PageHeader, Spinner, StatusDot } from '@/design-system';
import { cn } from '@/lib/cn';
import { type AiServiceRegistration, useAiServices } from './api/aiServicesApi';

/**
 * AI Service Registry (ARCHITECTURE.md §2 Screen 11, §6.1, §6.3, §6.4).
 *
 * Live today — ai-orchestrator's admin API is gateway-routed and gated on {@code ai:configure}.
 *
 * <p>Read-only, deliberately. §6.1 makes the registry a *deployment's* statement of what it runs, and
 * the orchestrator's own default profile ships it empty so that no deployment inherits services
 * nobody asked for. Editing it from here would put a second source of truth in front of the
 * configuration that governs inference routing; the screen therefore shows what is registered and
 * how it is behaving, and leaves changing it to configuration.
 *
 * <p>The **mode** column is the two-track switch (§6.4) made visible. `MOCK` means the boundary is
 * being served by the mock provider with no trained model behind it — which is the honest state of
 * this platform today, and is exactly why it is shown rather than hidden.
 */
export function AiServicesPage() {
  const { data, isLoading, isError, error } = useAiServices();
  const services = data ?? [];

  return (
    <div className="flex h-full flex-col">
      <PageHeader icon={BrainCircuit} title="AI Service Registry">
        <span className="text-xs text-muted-foreground">
          Routing by modality — the orchestrator never names a model (§6.1)
        </span>
      </PageHeader>

      {services.some((s) => s.mode.toUpperCase() === 'MOCK') && (
        <div
          role="status"
          className="flex items-center gap-2 border-b border-warn/30 bg-warn/10 px-4 py-2 text-xs text-warn"
        >
          <StatusDot status="warn" />
          <span>
            Mock inference in use. Classifications are produced by the mock provider, not a trained
            model — results carry <code className="font-mono">0.0.0-mock</code> and are never
            presented as real inference.
          </span>
        </div>
      )}

      <div className="flex-1 overflow-auto">
        {isLoading ? (
          <div className="grid h-full place-items-center"><Spinner /></div>
        ) : isError ? (
          <p role="alert" className="p-8 text-center text-sm text-critical">
            Could not load the AI service registry
            {error instanceof Error ? `: ${error.message}` : '.'}
          </p>
        ) : services.length === 0 ? (
          <p className="p-8 text-center text-sm text-muted-foreground">
            No AI services are registered. The registry is empty by default so that no deployment
            inherits services it did not ask for (§6.1); services are declared in configuration.
          </p>
        ) : (
          <table className="w-full text-xs">
            <thead className="sticky top-0 bg-surface">
              <tr className="border-b border-border text-left text-subtle-foreground">
                <th scope="col" className="px-4 py-2 font-medium">Health</th>
                <th scope="col" className="px-4 py-2 font-medium">Service</th>
                <th scope="col" className="px-4 py-2 font-medium">Modalities</th>
                <th scope="col" className="px-4 py-2 font-medium">Mode</th>
                <th scope="col" className="px-4 py-2 font-medium">Model</th>
                <th scope="col" className="px-4 py-2 font-medium text-right">Timeout</th>
                <th scope="col" className="px-4 py-2 font-medium text-right">Max concurrent</th>
                <th scope="col" className="px-4 py-2 font-medium">Canary</th>
              </tr>
            </thead>
            <tbody>
              {services.map((s) => (
                <ServiceRow key={s.id} service={s} />
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

function ServiceRow({ service }: { service: AiServiceRegistration }) {
  const mock = service.mode.toUpperCase() === 'MOCK';
  return (
    <tr className="border-b border-border/50 hover:bg-surface-2">
      <td className="px-4 py-1.5">
        <span className="flex items-center gap-1.5">
          <StatusDot status={service.healthy ? 'ok' : 'down'} />
          <span className={cn('font-medium', service.healthy ? 'text-ok' : 'text-down')}>
            {service.healthy ? 'HEALTHY' : 'UNAVAILABLE'}
          </span>
        </span>
      </td>
      <td className="px-4 py-1.5 font-mono">{service.id}</td>
      <td className="px-4 py-1.5">
        <span className="flex flex-wrap gap-1">
          {service.modalities.map((m) => (
            <Badge key={m} variant="neutral">{m}</Badge>
          ))}
        </span>
      </td>
      <td className="px-4 py-1.5">
        <Badge variant={mock ? 'warn' : 'primary'}>{service.mode.toUpperCase()}</Badge>
      </td>
      <td className="px-4 py-1.5 font-mono text-muted-foreground">
        {service.modelVersion || (mock ? '0.0.0-mock' : '——')}
      </td>
      {/* The p95 budget the orchestrator enforces per call (§6.3); the breaker opens against it. */}
      <td className="tnum px-4 py-1.5 text-right text-muted-foreground">{service.timeoutMs} ms</td>
      <td className="tnum px-4 py-1.5 text-right text-muted-foreground">{service.maxConcurrent}</td>
      <td className="px-4 py-1.5 text-muted-foreground">
        {service.canaryVersion
          ? `${service.canaryVersion} @ ${service.canaryPercentage}%`
          : '——'}
      </td>
    </tr>
  );
}
