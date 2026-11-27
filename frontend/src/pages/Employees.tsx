import { type FormEvent, useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { canManageEmployees, canViewEmployees, readMe } from '../auth/claims'
import { fetchWithAuth, getTokens } from '../auth/tokens'

// 백엔드 공통 응답 형식 (user의 ApiResponse)
type ApiResponse<T> = { status: number; success: boolean; message: string; data?: T }
type Employee = {
  id: number
  name: string
  email: string
  phoneNum: string
  rank: string // 한글 이름 ("팀장")
  regionId: number
  region: string
  workTypeId: number
  workType: string
}
type Region = { regionId: number; regionName: string }
type WorkType = { workTypeId: number; workTypeName: string }

// user의 Rank enum. 서버는 enum 이름으로 받고, 응답은 한글 이름으로 준다
const RANKS = [
  ['EMPLOYEE', '사원'],
  ['LEADER', '팀장'],
  ['WAREHOUSE_MANAGER', '창고 관리자'],
  ['BRANCH_ENGINEER', '엔지니어'],
] as const
const rankCode = (label: string) => RANKS.find(([, l]) => l === label)?.[0] ?? 'EMPLOYEE'

const API = '/user/api/v1'

async function call<T>(url: string, init?: RequestInit): Promise<{ ok: boolean; text: string; data?: T }> {
  const res = await fetchWithAuth(url, init)
  const body = (await res.json().catch(() => null)) as ApiResponse<T> | null
  // 403(권한 없음), 400(이메일 변경 등)은 서버 message를 그대로 보여준다
  return { ok: res.ok, text: res.ok ? (body?.message ?? '성공') : `${res.status}: ${body?.message ?? res.statusText}`, data: body?.data }
}

const json = (method: string, body: object): RequestInit => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

export default function Employees() {
  const me = readMe(getTokens()!.access_token)
  const canManage = canManageEmployees(me)
  const [employees, setEmployees] = useState<Employee[]>([])
  const [regions, setRegions] = useState<Region[]>([])
  const [workTypes, setWorkTypes] = useState<WorkType[]>([])
  const [keyword, setKeyword] = useState('')
  const [editing, setEditing] = useState<Employee | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const load = useCallback(async () => {
    const res = await call<{ content: Employee[] }>(`${API}/getAllUser?size=50&keyword=${encodeURIComponent(keyword)}`)
    if (res.ok) setEmployees(res.data?.content ?? [])
    else setMessage(res.text)
  }, [keyword])

  useEffect(() => {
    call<Region[]>(`${API}/getRegion`).then((r) => setRegions(r.data ?? []))
    call<WorkType[]>(`${API}/getWorkType`).then((r) => setWorkTypes(r.data ?? []))
  }, [])

  useEffect(() => {
    // 본사가 아니면 목록을 부르지 않는다. 불러도 서버가 403을 준다
    // me는 렌더마다 새 객체라 의존성에 넣으면 무한 반복된다. 토큰 클레임은 화면이 떠 있는 동안 바뀌지 않는다
    if (canViewEmployees(me)) load()
  }, [load])

  async function remove(e: Employee) {
    if (!confirm(`${e.name}(${e.email})을 삭제할까요? auth 로그인 계정도 같이 지워져요.`)) return
    const res = await call(`${API}/deleteUser?email=${encodeURIComponent(e.email)}`, { method: 'DELETE' })
    setMessage(res.text)
    if (res.ok) load()
  }

  return (
    <main>
      <p>
        <Link to="/">← 홈</Link>
      </p>
      <h1>직원 관리</h1>
      <p>
        나: {me.workType} {me.rank} (id {me.id})
      </p>
      {message && <p role="status">{message}</p>}

      {!canViewEmployees(me) ? (
        <p>직원 목록은 본사만 볼 수 있어요.</p>
      ) : (
        <>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              load()
            }}
          >
            <label>
              검색(이름·전화·이메일) <input value={keyword} onChange={(e) => setKeyword(e.target.value)} />
            </label>
          </form>
          <table>
            <thead>
              <tr>
                <th>이름</th>
                <th>이메일</th>
                <th>근무</th>
                <th>직급</th>
                {canManage && <th />}
              </tr>
            </thead>
            <tbody>
              {employees.map((e) => (
                <tr key={e.id}>
                  <td>{e.name}</td>
                  <td>{e.email}</td>
                  <td>
                    {e.workType} · {e.region}
                  </td>
                  <td>{e.rank}</td>
                  {canManage && (
                    <td>
                      <button type="button" onClick={() => setEditing(e)}>
                        수정
                      </button>
                      {String(e.id) !== me.id && (
                        <button type="button" onClick={() => remove(e)}>
                          삭제
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      {canManage && editing && (
        <EditForm
          key={editing.id}
          employee={editing}
          isSelf={String(editing.id) === me.id}
          regions={regions}
          workTypes={workTypes}
          onDone={(text, ok) => {
            setMessage(text)
            if (ok) {
              setEditing(null)
              load()
            }
          }}
        />
      )}
      {canManage && <RegisterForm regions={regions} workTypes={workTypes} onDone={(text, ok) => (setMessage(text), ok && load())} />}
    </main>
  )
}

type FormProps = { regions: Region[]; workTypes: WorkType[]; onDone: (text: string, ok: boolean) => void }

function RoleFields({ regions, workTypes, disabled, initial }: Omit<FormProps, 'onDone'> & { disabled?: boolean; initial?: Employee }) {
  return (
    <>
      <label>
        직급
        <select name="rank" defaultValue={initial ? rankCode(initial.rank) : 'EMPLOYEE'} disabled={disabled}>
          {RANKS.map(([code, label]) => (
            <option key={code} value={code}>
              {label}
            </option>
          ))}
        </select>
      </label>
      <label>
        지역
        <select name="regionId" defaultValue={initial?.regionId} disabled={disabled}>
          {regions.map((r) => (
            <option key={r.regionId} value={r.regionId}>
              {r.regionName}
            </option>
          ))}
        </select>
      </label>
      <label>
        근무유형
        <select name="workTypeId" defaultValue={initial?.workTypeId} disabled={disabled}>
          {workTypes.map((w) => (
            <option key={w.workTypeId} value={w.workTypeId}>
              {w.workTypeName}
            </option>
          ))}
        </select>
      </label>
    </>
  )
}

function RegisterForm({ regions, workTypes, onDone }: FormProps) {
  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = e.currentTarget
    const f = new FormData(form)
    const res = await call(
      `${API}/registerUser`,
      json('POST', {
        name: f.get('name'),
        email: f.get('email'),
        personalEmail: f.get('personalEmail'),
        phoneNum: f.get('phoneNum'),
        rank: f.get('rank'),
        regionId: Number(f.get('regionId')),
        workTypeId: Number(f.get('workTypeId')),
      }),
    )
    onDone(res.ok ? '등록했어요. 임시 비밀번호는 개인 메일(Mailpit)로 가요.' : res.text, res.ok)
    if (res.ok) form.reset()
  }

  return (
    <form onSubmit={onSubmit}>
      <h2>직원 등록</h2>
      <label>
        이름 <input name="name" required />
      </label>
      <label>
        회사 이메일(로그인 ID, 나중에 못 바꿔요) <input name="email" type="email" required />
      </label>
      <label>
        개인 이메일(임시 비밀번호 받을 곳) <input name="personalEmail" type="email" required />
      </label>
      <label>
        전화번호(숫자 10~11자리) <input name="phoneNum" pattern="[0-9]{10,11}" />
      </label>
      <RoleFields regions={regions} workTypes={workTypes} />
      <button type="submit">등록</button>
    </form>
  )
}

function EditForm({ employee, isSelf, regions, workTypes, onDone }: FormProps & { employee: Employee; isSelf: boolean }) {
  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const f = new FormData(e.currentTarget)
    // 자기 자신은 직급·지역·근무유형을 못 바꾼다(select가 disabled라 FormData에 없음 → 기존 값 그대로)
    const res = await call(
      `${API}/updateUser`,
      json('POST', {
        userId: employee.id,
        name: f.get('name'),
        email: employee.email,
        phoneNum: f.get('phoneNum'),
        rank: isSelf ? rankCode(employee.rank) : f.get('rank'),
        regionId: isSelf ? employee.regionId : Number(f.get('regionId')),
        workTypeId: isSelf ? employee.workTypeId : Number(f.get('workTypeId')),
      }),
    )
    onDone(res.text, res.ok)
  }

  return (
    <form onSubmit={onSubmit}>
      <h2>직원 수정: {employee.email}</h2>
      <label>
        이름 <input name="name" defaultValue={employee.name} required />
      </label>
      <label>
        회사 이메일 <input value={employee.email} readOnly />
      </label>
      <label>
        전화번호 <input name="phoneNum" defaultValue={employee.phoneNum} pattern="[0-9]{10,11}" />
      </label>
      <RoleFields regions={regions} workTypes={workTypes} disabled={isSelf} initial={employee} />
      {isSelf && <p>자기 자신의 직급·지역·근무유형은 바꿀 수 없어요.</p>}
      <button type="submit">저장</button>
    </form>
  )
}
