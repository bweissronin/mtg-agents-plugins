/**
 * Configuration management
 */

import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';
import * as yaml from 'js-yaml';
import { Config, defaultConfig } from './types';

const CONFIG_FILENAME = '.mtg-agents.json';
const CONFIG_FILENAME_YAML = '.mtg-agents.yaml';

export function loadConfig(configPath?: string): Config {
    // Start with defaults
    let config: Config = JSON.parse(JSON.stringify(defaultConfig));

    // Load from config file (if specified or found)
    const configFile = configPath || findConfigFile();
    if (configFile && fs.existsSync(configFile)) {
        try {
            const content = fs.readFileSync(configFile, 'utf-8');
            const fileConfig = configFile.endsWith('.yaml') || configFile.endsWith('.yml')
                ? yaml.load(content) as Partial<Config>
                : JSON.parse(content) as Partial<Config>;
            config = mergeConfig(config, fileConfig);
        } catch (e) {
            console.warn(`Warning: Could not parse config file ${configFile}`);
        }
    }

    // Override with environment variables
    config = applyEnvOverrides(config);

    return config;
}

export function findConfigFile(): string | null {
    // Check current directory
    const localJson = path.join(process.cwd(), CONFIG_FILENAME);
    if (fs.existsSync(localJson)) return localJson;

    const localYaml = path.join(process.cwd(), CONFIG_FILENAME_YAML);
    if (fs.existsSync(localYaml)) return localYaml;

    // Check home directory
    const homeDir = os.homedir();
    const globalConfig = path.join(homeDir, '.config', 'mtg-agents', 'config.json');
    if (fs.existsSync(globalConfig)) return globalConfig;

    return null;
}

export function saveConfig(config: Partial<Config>, global: boolean = false): string {
    const configPath = global
        ? path.join(os.homedir(), '.config', 'mtg-agents', 'config.json')
        : path.join(process.cwd(), CONFIG_FILENAME);

    // Ensure directory exists
    const dir = path.dirname(configPath);
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
    }

    fs.writeFileSync(configPath, JSON.stringify(config, null, 2));
    return configPath;
}

export function getConfigValue(config: Config, key: string): any {
    const parts = key.split('.');
    let value: any = config;
    for (const part of parts) {
        if (value && typeof value === 'object' && part in value) {
            value = value[part];
        } else {
            return undefined;
        }
    }
    return value;
}

export function setConfigValue(config: Config, key: string, value: any): Config {
    const parts = key.split('.');
    const newConfig = JSON.parse(JSON.stringify(config));
    let current: any = newConfig;

    for (let i = 0; i < parts.length - 1; i++) {
        if (!(parts[i] in current)) {
            current[parts[i]] = {};
        }
        current = current[parts[i]];
    }

    current[parts[parts.length - 1]] = value;
    return newConfig;
}

function mergeConfig(base: Config, override: Partial<Config>): Config {
    const result = JSON.parse(JSON.stringify(base));

    function merge(target: any, source: any) {
        for (const key in source) {
            if (source[key] && typeof source[key] === 'object' && !Array.isArray(source[key])) {
                if (!target[key]) target[key] = {};
                merge(target[key], source[key]);
            } else {
                target[key] = source[key];
            }
        }
    }

    merge(result, override);
    return result;
}

function applyEnvOverrides(config: Config): Config {
    const envMappings: Record<string, string> = {
        'MTG_AGENTS_ART_SOURCE': 'art.source',
        'MTG_AGENTS_STABILITY_KEY': 'art.stabilityApiKey',
        'MTG_AGENTS_LOCAL_SD_URL': 'art.localSdUrl',
        'MTG_AGENTS_CACHE_DIR': 'art.cacheDir',
        'MTG_AGENTS_CARD_STYLE': 'style.cardStyle',
        'MTG_AGENTS_THEME': 'style.theme',
        'MTG_AGENTS_OUTPUT_FORMAT': 'output.format',
        'MTG_AGENTS_OUTPUT_DIR': 'output.outputDir',
        'MTG_AGENTS_SERVER_PORT': 'server.port'
    };

    for (const [envVar, configPath] of Object.entries(envMappings)) {
        const value = process.env[envVar];
        if (value !== undefined) {
            // Handle type conversion
            let parsedValue: any = value;
            if (configPath === 'server.port') {
                parsedValue = parseInt(value, 10);
            } else if (value === 'true') {
                parsedValue = true;
            } else if (value === 'false') {
                parsedValue = false;
            }
            config = setConfigValue(config, configPath, parsedValue);
        }
    }

    return config;
}

export function printConfig(config: Config): void {
    console.log(JSON.stringify(config, null, 2));
}
