import { useEffect, useRef } from 'react'
import * as THREE from 'three'

export interface TopologyFieldProps {
  className?: string
  style?: React.CSSProperties
}

export default function TopologyField({ className, style }: TopologyFieldProps) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const glowRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    const container = containerRef.current
    const canvas = canvasRef.current
    if (!container || !canvas) return

    let width = container.clientWidth || 400
    let height = container.clientHeight || 700

    const scene = new THREE.Scene()
    scene.fog = new THREE.Fog(0xffffff, 600, 2200)

    const fov = 50
    const camera = new THREE.PerspectiveCamera(fov, width / height, 1, 3000)
    camera.position.z = 700

    const renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true })
    renderer.setSize(width, height)
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))

    const group = new THREE.Group()
    scene.add(group)

    const numNodes = 135
    const nodes: THREE.Mesh[] = []
    const nodeGeo = new THREE.SphereGeometry(1, 16, 16)

    for (let i = 0; i < numNodes; i++) {
      const phi = Math.acos(-1 + (2 * i) / numNodes)
      const theta = Math.sqrt(numNodes * Math.PI) * phi
      const x = Math.cos(theta) * Math.sin(phi)
      const y = Math.sin(theta) * Math.sin(phi)
      const z = Math.cos(phi)

      const isHero = i === 12 || i === 48 || i === 92
      const isFeature = !isHero && (i % 11 === 0 || i === 28 || i === 70)
      const isMedium = !isHero && !isFeature && (i % 4 === 0)

      const baseSize = isHero ? 5.2 : isFeature ? 3.5 : isMedium ? 2.3 : Math.random() * 0.9 + 1.4
      const minOpacity = isHero ? 0.95 : isFeature ? 0.90 : isMedium ? 0.82 : 0.72

      // Vibrant, luminous blues tailored to pop brightly against the light background
      const nodeColor = isHero
        ? 0x0066ff
        : isFeature
        ? 0x1877f2
        : isMedium
        ? 0x0284c7
        : (i % 3 === 0 ? 0x38bdf8 : 0x2563eb)

      const mesh = new THREE.Mesh(
        nodeGeo,
        new THREE.MeshBasicMaterial({ color: nodeColor, transparent: true, opacity: minOpacity })
      )
      mesh.position.set(x, y, z)
      mesh.userData = {
        baseSize,
        minOpacity,
        pulseSpeed: isHero ? 0.015 : Math.random() * 0.02 + 0.012,
        pulseOffset: Math.random() * Math.PI * 2,
      }
      group.add(mesh)
      nodes.push(mesh)
    }

    const linePos: number[] = []
    const lineColors: number[] = []
    for (let i = 0; i < numNodes; i++) {
      for (let j = i + 1; j < numNodes; j++) {
        const dist = nodes[i].position.distanceTo(nodes[j].position)
        const threshold = 0.44
        if (dist < threshold) {
          linePos.push(nodes[i].position.x, nodes[i].position.y, nodes[i].position.z)
          linePos.push(nodes[j].position.x, nodes[j].position.y, nodes[j].position.z)

          const factor = 1 - (dist / threshold) * 0.35
          lineColors.push(0.09 * factor, 0.47 * factor, 0.96 * factor)
          lineColors.push(0.09 * factor, 0.47 * factor, 0.96 * factor)
        }
      }
    }

    const lineGeo = new THREE.BufferGeometry()
    lineGeo.setAttribute('position', new THREE.Float32BufferAttribute(linePos, 3))
    lineGeo.setAttribute('color', new THREE.Float32BufferAttribute(lineColors, 3))
    const lineMat = new THREE.LineBasicMaterial({
      vertexColors: true,
      transparent: true,
      blending: THREE.NormalBlending,
      depthWrite: false,
      opacity: 0.88,
    })
    const lines = new THREE.LineSegments(lineGeo, lineMat)
    group.add(lines)

    function resize() {
      if (!container) return
      width = container.clientWidth
      height = container.clientHeight
      if (width === 0 || height === 0) return

      camera.aspect = width / height
      camera.updateProjectionMatrix()
      renderer.setSize(width, height)

      // Visible dimension at z = 0 with camera at z = 700
      const vFov = (fov * Math.PI) / 180
      const visibleHeight = 2 * Math.tan(vFov / 2) * camera.position.z
      const visibleWidth = visibleHeight * camera.aspect

      const halfW = visibleWidth / 2
      const halfH = visibleHeight / 2

      // Positioned at the bottom-center with comfortable breathing room below text
      const R = Math.min(visibleWidth * 0.56, visibleHeight * 0.38)
      group.scale.set(R, R, R)

      // Centered horizontally (0) and anchored at the bottom edge
      const baseCenterX = 0
      const baseCenterY = -halfH + R * 0.12
      group.position.set(baseCenterX, baseCenterY, 0)

      if (glowRef.current) {
        const leftPct = ((baseCenterX + halfW) / visibleWidth) * 100
        const topPct = ((halfH - baseCenterY) / visibleHeight) * 100
        glowRef.current.style.left = `${leftPct}%`
        glowRef.current.style.top = `${topPct}%`
        glowRef.current.style.width = `${R * 2.3}px`
        glowRef.current.style.height = `${R * 2.3}px`
      }
    }

    const resizeObserver = new ResizeObserver(() => {
      resize()
    })
    resizeObserver.observe(container)
    resize()

    let mouseX = 0
    let mouseY = 0
    let targetX = 0
    let targetY = 0

    const handleMouseMove = (e: MouseEvent) => {
      const rect = container.getBoundingClientRect()
      mouseX = (e.clientX - (rect.left + rect.width / 2)) * 0.0004
      mouseY = (e.clientY - (rect.top + rect.height / 2)) * 0.0004
    }

    window.addEventListener('mousemove', handleMouseMove)

    let animationId: number
    let time = 0

    function animate() {
      animationId = requestAnimationFrame(animate)
      time += 1

      targetX += (mouseX - targetX) * 0.04
      targetY += (mouseY - targetY) * 0.04

      group.rotation.y = time * 0.0014 + targetX
      group.rotation.x = 0.18 + targetY
      group.rotation.z = time * 0.0004

      nodes.forEach((mesh) => {
        const p = mesh.userData
        const pulse = (Math.sin(time * p.pulseSpeed + p.pulseOffset) + 1) / 2

        const targetRadius = p.baseSize + pulse * 2.2
        const scale = targetRadius / group.scale.x

        mesh.scale.set(scale, scale, scale)
        ;(mesh.material as THREE.MeshBasicMaterial).opacity =
          p.minOpacity + pulse * (1 - p.minOpacity)
      })

      renderer.render(scene, camera)
    }

    animate()

    return () => {
      cancelAnimationFrame(animationId)
      resizeObserver.disconnect()
      window.removeEventListener('mousemove', handleMouseMove)
      renderer.dispose()
      nodeGeo.dispose()
      lineGeo.dispose()
      lineMat.dispose()
    }
  }, [])

  return (
    <div
      ref={containerRef}
      className={className}
      style={{
        position: 'absolute',
        inset: 0,
        width: '100%',
        height: '100%',
        overflow: 'hidden',
        pointerEvents: 'none',
        ...style,
      }}
    >
      <div
        ref={glowRef}
        style={{
          position: 'absolute',
          borderRadius: '50%',
          background:
            'radial-gradient(circle, rgba(24, 119, 242, 0.02) 0%, rgba(56, 189, 248, 0.008) 35%, transparent 60%)',
          transform: 'translate(-50%, -50%)',
          pointerEvents: 'none',
          filter: 'blur(50px)',
          zIndex: 0,
        }}
      />
      <canvas
        ref={canvasRef}
        style={{
          display: 'block',
          width: '100%',
          height: '100%',
          position: 'absolute',
          inset: 0,
          zIndex: 1,
        }}
      />
    </div>
  )
}
