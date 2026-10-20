import { randomUUID } from 'node:crypto'
import fs from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const scriptPath = await fs.realpath(fileURLToPath(import.meta.url))
const scriptDirectory = path.dirname(scriptPath)
const repoRoot = path.resolve(scriptDirectory, '../../..')
const distDirectory = path.resolve(scriptDirectory, '../dist')
const assetsWebDirectory = path.resolve(repoRoot, 'app/src/main/assets/web')
const targetDirectory = path.resolve(assetsWebDirectory, 'vue')
const targetRelativePath = path.relative(repoRoot, targetDirectory)

const isInside = (root, target) => {
  const relative = path.relative(root, target)
  return relative !== '' && !relative.startsWith(`..${path.sep}`) && !path.isAbsolute(relative)
}

const assertInside = (root, target, label) => {
  if (!isInside(root, target)) {
    throw new Error(`${label} must stay inside ${root}: ${target}`)
  }
}

const assertNoSymlinkPath = async (root, target) => {
  assertInside(root, target, 'Path')
  const rootStat = await fs.lstat(root)
  if (!rootStat.isDirectory() || rootStat.isSymbolicLink()) {
    throw new Error(`Expected a real directory at ${root}`)
  }

  let current = root
  const relative = path.relative(root, target)
  for (const segment of relative.split(path.sep).filter(Boolean)) {
    current = path.join(current, segment)
    try {
      const stat = await fs.lstat(current)
      if (stat.isSymbolicLink()) {
        throw new Error(`Refusing to follow a symlink or junction: ${current}`)
      }
    } catch (error) {
      if (error && typeof error === 'object' && 'code' in error && error.code === 'ENOENT') {
        break
      }
      throw error
    }
  }
}

const pathExists = async target => {
  try {
    await fs.lstat(target)
    return true
  } catch (error) {
    if (error && typeof error === 'object' && 'code' in error && error.code === 'ENOENT') {
      return false
    }
    throw error
  }
}

const syncAndroidAssets = async () => {
  assertInside(repoRoot, distDirectory, 'Build output')
  assertInside(repoRoot, assetsWebDirectory, 'Android assets directory')
  assertInside(assetsWebDirectory, targetDirectory, 'Android Web target')
  if (targetRelativePath !== path.join('app', 'src', 'main', 'assets', 'web', 'vue')) {
    throw new Error(`Unexpected Android Web target: ${targetRelativePath}`)
  }

  await assertNoSymlinkPath(repoRoot, distDirectory)
  await assertNoSymlinkPath(repoRoot, assetsWebDirectory)
  await assertNoSymlinkPath(assetsWebDirectory, targetDirectory)
  const distStat = await fs.stat(distDirectory)
  if (!distStat.isDirectory()) throw new Error(`Build output is not a directory: ${distDirectory}`)

  if (await pathExists(targetDirectory)) {
    const targetStat = await fs.lstat(targetDirectory)
    if (!targetStat.isDirectory() || targetStat.isSymbolicLink()) {
      throw new Error(`Android Web target must be a real directory: ${targetDirectory}`)
    }
  }

  const suffix = `${process.pid}-${randomUUID()}`
  const stageDirectory = path.join(assetsWebDirectory, `.vue-sync-${suffix}`)
  const backupDirectory = path.join(assetsWebDirectory, `.vue-backup-${suffix}`)
  assertInside(assetsWebDirectory, stageDirectory, 'Staging directory')
  assertInside(assetsWebDirectory, backupDirectory, 'Backup directory')
  if ((await pathExists(stageDirectory)) || (await pathExists(backupDirectory))) {
    throw new Error('Generated synchronization paths already exist; refusing to overwrite them.')
  }

  let previousTargetMoved = false
  try {
    await fs.cp(distDirectory, stageDirectory, {
      recursive: true,
      errorOnExist: true,
      force: false,
      dereference: false,
    })

    if (await pathExists(targetDirectory)) {
      await fs.rename(targetDirectory, backupDirectory)
      previousTargetMoved = true
    }

    try {
      await fs.rename(stageDirectory, targetDirectory)
    } catch (error) {
      if (previousTargetMoved) {
        try {
          await fs.rename(backupDirectory, targetDirectory)
          previousTargetMoved = false
        } catch (restoreError) {
          throw new AggregateError(
            [error, restoreError],
            `Resource replacement failed and the previous target could not be restored; backup remains at ${backupDirectory}`,
          )
        }
      }
      throw error
    }

    if (previousTargetMoved) {
      try {
        await fs.rm(backupDirectory, { recursive: true })
      } catch (error) {
        console.warn(`New assets are live, but the old copy remains at ${backupDirectory}:`, error)
      }
    }
    console.log(`Synced Web assets to ${targetRelativePath}`)
  } catch (error) {
    if (await pathExists(stageDirectory)) {
      try {
        await fs.rm(stageDirectory, { recursive: true })
      } catch (cleanupError) {
        console.warn(`Could not remove the generated staging directory ${stageDirectory}:`, cleanupError)
      }
    }
    throw error
  }
}

const args = process.argv.slice(2)
if (args.length > 1 || (args.length === 1 && args[0] !== '--android')) {
  console.error('Usage: node ./scripts/sync.js [--android]')
  process.exitCode = 2
} else if (args[0] === '--android' || process.env.GITHUB_ENV) {
  try {
    await syncAndroidAssets()
  } catch (error) {
    console.error('Web asset synchronization failed:', error)
    process.exitCode = 1
  }
} else {
  console.log('Local asset copy skipped. Run `pnpm run sync:android` to sync the Android Web assets.')
}
