import { SlidersHorizontal } from 'lucide-react';
import { useState } from 'react';
import { Badge, Button, Input, PageHeader, Panel, PanelBody, PanelHeader, PanelTitle } from '@/design-system';
import { CONFIG_REST_ROUTED, useConfig, useConfigActions, useConfigHistory } from './api/configApi';

/**
 * System configuration (ARCHITECTURE.md §2 Screen 11, §4.1.12): runtime namespaces with a full version
 * history and rollback, without touching a file or restarting a service. Built against config-service's
 * real contract but gated until M11 routes it (G-4); reads need {@code policy:read} and a write needs
 * the namespace kind's §7.4 permission and a justification, all enforced server-side.
 */
export function ConfigPage() {
  const [draft, setDraft] = useState('');
  const [namespace, setNamespace] = useState<string | null>(null);
  const config = useConfig(namespace);
  const history = useConfigHistory(namespace);
  const { rollback } = useConfigActions();
  const gated = !CONFIG_REST_ROUTED;

  return (
    <div className="flex h-full flex-col">
      <PageHeader icon={SlidersHorizontal} title="Configuration" />
      {gated && (
        <p className="border-b border-border bg-elevated/40 px-4 py-1.5 text-2xs text-subtle-foreground">
          Runtime configuration arrives when the gateway routes config-service (M11). The namespace
          view, version history and rollback are built against the real contract.
        </p>
      )}

      <form
        onSubmit={(e) => {
          e.preventDefault();
          setNamespace(draft.trim() || null);
        }}
        className="flex items-center gap-2 border-b border-border px-4 py-2"
      >
        <Input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="Namespace e.g. threat.scoring.policy"
          aria-label="Configuration namespace"
          className="h-8 w-72 text-xs"
        />
        <Button type="submit" size="sm" variant="secondary" disabled={gated}>
          Load
        </Button>
      </form>

      <div className="grid flex-1 grid-cols-1 gap-3 overflow-auto p-3 lg:grid-cols-[1fr_320px]">
        <Panel>
          <PanelHeader>
            <PanelTitle>Current value</PanelTitle>
            {config.data && <Badge variant="neutral">v{config.data.version}</Badge>}
          </PanelHeader>
          <PanelBody>
            {!namespace ? (
              <p className="text-xs text-subtle-foreground">Load a namespace to view its current value.</p>
            ) : config.isLoading ? (
              <p className="text-xs text-subtle-foreground">Loading…</p>
            ) : config.data ? (
              <pre className="tnum overflow-x-auto rounded-sm bg-background p-2 text-2xs text-muted-foreground">
                {JSON.stringify(config.data.value, null, 2)}
              </pre>
            ) : (
              <p className="text-xs text-subtle-foreground">Awaiting gateway route (M11).</p>
            )}
          </PanelBody>
        </Panel>

        <Panel>
          <PanelHeader>
            <PanelTitle>Version history</PanelTitle>
          </PanelHeader>
          <PanelBody className="space-y-1.5">
            {(history.data ?? []).length === 0 ? (
              <p className="text-xs text-subtle-foreground">
                {namespace ? 'Awaiting gateway route (M11).' : '—'}
              </p>
            ) : (
              history.data?.map((version) => (
                <div key={version.version} className="flex items-center justify-between gap-2 rounded-sm border border-border px-2 py-1.5 text-xs">
                  <span className="tnum">v{version.version}</span>
                  <span className="min-w-0 flex-1 truncate text-subtle-foreground">{version.justification}</span>
                  <Button
                    size="sm"
                    variant="ghost"
                    disabled={gated || rollback.isPending}
                    onClick={() =>
                      namespace &&
                      rollback.mutate({ namespace, toVersion: version.version, justification: 'Rollback from console' })
                    }
                  >
                    Roll back
                  </Button>
                </div>
              ))
            )}
          </PanelBody>
        </Panel>
      </div>
    </div>
  );
}
