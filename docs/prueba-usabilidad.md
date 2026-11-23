# Prueba de usabilidad del portal público · RNF-10

**Lo que pide RNF-10 (y HU-04, criterio 5):** en una prueba de usabilidad, **al menos el 90 %** de los participantes **busca una especie e identifica su información pública principal sin ayuda**.

Con 10 participantes, deben lograrlo **9 o más**. Esta prueba la hace el equipo con personas reales: no se puede automatizar.

## Antes de empezar

1. Levanten BioHub y carguen los datos de demostración:

   ```bash
   docker compose up -d --build
   ./scripts/sembrar-demo.sh
   ```

2. Abran `http://localhost:8080` en un computador o en un celular. Cada participante empieza **en la página de inicio, sin haber buscado nada**.
3. Los participantes **no deben saber de software**: familiares, compañeros de otras carreras, vecinos. Mínimo 10. Mejor si hay variedad de edades.
4. Una persona del equipo **lee el guion** y otra **toma nota**. Nadie ayuda durante la tarea.

## Guion (leerlo igual a todos)

> "Gracias por ayudarnos. Vamos a probar una página web, **no a usted**: si algo es difícil, es culpa de la página. No podemos ayudarle durante la tarea; diga en voz alta lo que piensa mientras la hace. Puede rendirse cuando quiera."
>
> **Tarea:** "Usted oyó que en Colombia vive un mono llamado *mono araña café*. Encuentre en esta página la información de esa especie y dígame: **¿está en peligro? ¿En qué departamentos se ha visto?**"

## Cuándo cuenta como "lo logró"

**Lo logró sin ayuda** si se cumplen las tres cosas:
1. abre la ficha del mono araña café (*Ateles hybridus*);
2. responde bien si está **en peligro**;
3. nombra **al menos un departamento** donde se ha visto.

Todo eso **sin que nadie le diga nada**, y en **menos de 3 minutos**.

**No lo logró** si pide ayuda, se rinde, se pasa de 3 minutos o responde mal.

## Hoja de registro

| # | Edad | ¿Usa internet a diario? | Dispositivo | Tiempo (s) | Abrió la ficha | ¿En peligro? | Departamento | ¿Lo logró sin ayuda? | Dónde dudó (lo que dijo) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | | | | | | | | | |
| 2 | | | | | | | | | |
| 3 | | | | | | | | | |
| 4 | | | | | | | | | |
| 5 | | | | | | | | | |
| 6 | | | | | | | | | |
| 7 | | | | | | | | | |
| 8 | | | | | | | | | |
| 9 | | | | | | | | | |
| 10 | | | | | | | | | |

## Resultado

```
Porcentaje = (participantes que lo lograron sin ayuda / total de participantes) × 100
```

| Dato | Valor |
| --- | --- |
| Participantes | |
| Lo lograron sin ayuda | |
| Porcentaje | |
| ¿Cumple RNF-10 (≥ 90 %)? | |
| Tiempo promedio de los que lo lograron | |
| Problemas que se repitieron (2 o más personas) | |

## Después de la prueba

- **Si cumple:** anoten el resultado y los problemas pequeños que vieron. Cuéntenlo en la sustentación: "9 de 10 lo lograron; dos dudaron en …".
- **Si no cumple:** miren en qué paso dudaron. Por ejemplo: no encontraron el buscador, no entendieron "hallazgos" o no vieron el aviso de peligro. Cambien eso en el portal (`infra/nginx/html/index.html`) y repitan con **otras** personas. Contar ese ciclo de "medir, cambiar y volver a medir" vale más en la sustentación que un 100 % a la primera.
- Guarden la hoja llena (foto o archivo) como evidencia.

## Opcional: la consola científica

RNF-10 habla **solo del portal público**. Si quieren evidencia extra, pidan a 3 personas, haciendo el papel de investigador, que registren un hallazgo en `http://localhost:8080/consola/` (usuario `investigador@biohub.co`) y anoten dónde dudan. No cuenta para RNF-10.
