import type { VueWrapper } from '@vue/test-utils'
import { RouterLinkStub, flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { mocks } = vi.hoisted(() => ({
  mocks: {
    getFollowing: vi.fn(),
    getFollowers: vi.fn(),
    unfollowAuthor: vi.fn(),
    success: vi.fn(),
  },
}))
vi.mock('@/api/user', () => ({
  getFollowing: mocks.getFollowing,
  getFollowers: mocks.getFollowers,
}))
vi.mock('@/api/follow', () => ({ unfollowAuthor: mocks.unfollowAuthor }))
vi.mock('element-plus', () => ({ ElMessage: { success: mocks.success } }))

import ProfileFollowList from '../ProfileFollowList.vue'

const user = (id: string, username: string) => ({
  id,
  username,
  avatar: '',
  bio: '',
  occupation: '',
  followed: true,
  followsMe: false,
})

const page = (list: ReturnType<typeof user>[]) => ({
  code: 200,
  message: '操作成功',
  data: { list, total: list.length, page: 1, pageSize: 20 },
})

const deferred = <T>() => {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

const rowsOf = (wrapper: VueWrapper) => wrapper.findAll('.follow-row')
const actionOf = (wrapper: VueWrapper, index = 0) => wrapper.findAll('.follow-row__action')[index]

let wrapper: VueWrapper

beforeEach(() => {
  Object.values(mocks).forEach((fn) => fn.mockReset())
  mocks.getFollowing.mockResolvedValue(page([user('11', '甲'), user('22', '乙')]))
  mocks.getFollowers.mockResolvedValue(page([user('33', '丙')]))
  // 组件的两条 catch 会往控制台打堆栈，把真问题埋在一屏 error 里
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

const open = async () => {
  wrapper = mount(ProfileFollowList, {
    props: { kind: 'following' },
    global: {
      stubs: { RouterLink: RouterLinkStub, 'el-avatar': true },
      // v-loading 由 main.ts 全局注册，这里给一枚空钩子，免得解析告警盖住真问题
      directives: { loading: {} },
    },
  })
  await flushPromises()
}

describe('ProfileFollowList 的「取消关注」', () => {
  it('按 kind 取对应的列表，两栏不共用一次请求', async () => {
    await open()

    expect(mocks.getFollowing).toHaveBeenCalledWith({ page: 1, pageSize: 20 })
    expect(mocks.getFollowers).not.toHaveBeenCalled()
    expect(rowsOf(wrapper)).toHaveLength(2)
  })

  it('接口未答话前不摘行，也不动计数', async () => {
    const inflight = deferred<ReturnType<typeof page>>()
    mocks.unfollowAuthor.mockReturnValue(inflight.promise)
    await open()

    await actionOf(wrapper).trigger('click')
    await flushPromises()

    expect(mocks.unfollowAuthor).toHaveBeenCalledWith('11')
    // 乐观更新的做法是当场把这一行从 rows 里剔掉；服务端还没答话，行必须还在
    expect(rowsOf(wrapper)).toHaveLength(2)
    expect(actionOf(wrapper).attributes('disabled')).toBeDefined()
  })

  it('接口失败时维持服务端给的样子，不伪造成功', async () => {
    const inflight = deferred<ReturnType<typeof page>>()
    mocks.unfollowAuthor.mockReturnValue(inflight.promise)
    await open()

    await actionOf(wrapper).trigger('click')
    inflight.reject(new Error('network'))
    await flushPromises()

    expect(rowsOf(wrapper)).toHaveLength(2)
    expect(mocks.getFollowing).toHaveBeenCalledTimes(1)
    expect(mocks.success).not.toHaveBeenCalled()
    expect(wrapper.emitted('changed')).toBeUndefined()
    expect(actionOf(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('只有接口确认后才按服务端重拉，行数以重拉结果为准', async () => {
    mocks.unfollowAuthor.mockResolvedValue({
      code: 200,
      message: '操作成功',
      data: { followed: false, followers: 3 },
    })
    // 首屏两行，重拉后服务端只剩一行——行数变化只能来自这次重拉，不是本地摘除
    mocks.getFollowing
      .mockResolvedValueOnce(page([user('11', '甲'), user('22', '乙')]))
      .mockResolvedValueOnce(page([user('22', '乙')]))
    await open()
    expect(rowsOf(wrapper)).toHaveLength(2)

    await actionOf(wrapper).trigger('click')
    await flushPromises()

    expect(mocks.getFollowing).toHaveBeenCalledTimes(2)
    expect(rowsOf(wrapper)).toHaveLength(1)
    expect(rowsOf(wrapper)[0]?.text()).toContain('乙')
    expect(wrapper.emitted('changed')).toHaveLength(1)
  })
})
