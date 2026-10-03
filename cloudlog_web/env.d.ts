/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_APP_API_BASE_URL: string
  readonly VITE_APP_MINIO_ENDPOINT: string
  readonly VITE_APP_MINIO_BUCKET: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
