import axios from 'axios';

const api = axios.create({
    baseURL: 'http://localhost:8080/api',
    timeout: 300000 // 5 minutes for large comparisons
});

export interface DbConfig {
    host: string;
    port: number;
    username: string;
    password?: string;
    database: string;
}

export interface CompareRequest {
    source: DbConfig;
    target: DbConfig;
    ignoreFields?: string[];
    excludeTables?: string[];
    ignoreDataTables?: string[];
    specifiedPrimaryKeys?: string[];
    treeTableConfig?: string[];
    excludeDataRows?: string[];
    includeDataRows?: string[];
}

export const checkConnection = (config: DbConfig) => {
    return api.post('/connect/check', config);
};

export const compare = (request: CompareRequest) => {
    return api.post<{success: boolean, id: string}>('/compare', request);
};

export const getCompareTables = (id: string) => {
    return api.get<{tables: any[]}>('/compare/' + id + '/tables');
};

export const getTableDetail = (id: string, tableName: string) => {
    return api.get<{diff: TableDiff}>('/compare/' + id + '/table/' + tableName);
};

export const getTableSql = (id: string, tableName: string) => {
    return api.get<{upgradeSql: string, rollbackSql: string}>('/compare/' + id + '/table/' + tableName + '/sql');
};

export const getDownloadSqlUrl = (id: string, type: 'upgrade' | 'rollback') => {
    return `${api.defaults.baseURL}/compare/${id}/sql/download?type=${type}`;
};

export const listConfigs = () => {
    return api.get<string[]>('/config/list');
};

export const loadConfig = (filename: string) => {
    return api.get<CompareRequest>('/config/load', { params: { filename } });
};

export const saveConfig = (filename: string, config: CompareRequest) => {
    return api.post('/config/save', config, { params: { filename } });
};

// Env API
export interface EnvDbInfo {
    codePath: string;
    composePath: string;
    serviceName: string;
    excludeInitSql: string[];
    gitRef: string;
    containerPrefix: string;
    port: number;
    dbConfig: DbConfig;
}

export interface EnvConfig {
    name: string;
    separateCodePath?: boolean;
    source: EnvDbInfo;
    target: EnvDbInfo;
    ignoreFields?: string[];
    excludeTables?: string[];
    ignoreDataTables?: string[];
    specifiedPrimaryKeys?: string[];
    excludeDataRows?: string[];
    includeDataRows?: string[];
}

export interface DockerParams {
  codePath: string;
  composePath: string;
  prefix: string;
  serviceName: string;
  port: number;
  excludeInitSql?: string[];
  gitRef?: string;
}

export interface GitStatus {
    name: string;
    path: string;
    isDirectory: boolean;
}

export interface GitStatus {
    branch: string;
    isDirty: boolean;
    error?: string;
}

export interface TableDiff {
    tableName: string;
    rowCount: { source: number; target: number };
    structDiff?: any[];
    dataDiff?: {
        added: any[];
        removed: any[];
        modified: any[];
    };
}

export interface FileItem {
    name: string;
    path: string;
    isDirectory: boolean;
}

export const listEnvs = () => {
    return api.get<string[]>('/env/list');
};

export const loadEnv = (filename: string) => {
    return api.get<EnvConfig>('/env/load', { params: { filename } });
};

export const saveEnv = (config: EnvConfig) => {
    return api.post('/env/save', config);
};

export const generateConfig = (config: EnvConfig) => {
    return api.post('/env/generate', config);
};

export const startDocker = (params: DockerParams) => {
    return api.post('/env/docker/start', params);
};

export const stopDocker = (params: DockerParams) => {
    return api.post('/env/docker/stop', params);
};

export const getDockerStatus = (params: DockerParams) => {
    return api.post<{success: boolean, running: boolean}>('/env/docker/status', params);
};

export const getDockerStatusStreamUrl = (params: DockerParams) => {
    const query = new URLSearchParams({
        codePath: params.codePath,
        composePath: params.composePath,
        prefix: params.prefix,
        serviceName: params.serviceName,
        port: params.port.toString()
    }).toString();
    return `${api.defaults.baseURL}/env/docker/status/stream?${query}`;
};

export const listFiles = (path?: string) => {
    return api.get<FileItem[]>('/fs/list', { params: { path } });
};

export const getParentDir = (path: string) => {
    return api.get<FileItem>('/fs/parent', { params: { path } });
};

export const getGitStatus = (path: string) => {
    return api.get<GitStatus>('/git/status', { params: { path } });
};

export const findGitRoot = (path: string) => {
    return api.get<string>('/git/root', { params: { path } });
};
